package dev.automata.automata.automation;

import dev.automata.automata.dto.*;
import dev.automata.automata.model.Automation;
import dev.automata.automata.model.AutomationLog;
import dev.automata.automata.model.Device;
import dev.automata.automata.service.MainService;
import dev.automata.automata.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class AutomationEngine {

    private final RedisService redisService;
    private final MainService mainService;

    @Value("${app.location.lat}")
    private String LOCATION_LAT;
    @Value("${app.location.long}")
    private String LOCATION_LONG;


    private static final ScheduledExecutorService delayScheduler =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "automation-delay-scheduler");
                t.setDaemon(true);
                return t;
            });


    // ═════════════════════════════════════════════════════════════════════
    // GRAPH EVALUATION  (unchanged logic, now also stores wasActive per-branch)
    // ═════════════════════════════════════════════════════════════════════

    public NodeResult evaluate(Automation automation,
                               Map<String, Object> payload,
                               ExecutionContext ctx,
                               AutomationCache cache) {

        List<Automation.Condition> conditions = automation.getConditions() == null
                ? List.of() : automation.getConditions();
        List<Automation.Operator> operators = automation.getOperators() == null
                ? List.of() : automation.getOperators();

        Set<String> operatorIds = operators.stream()
                .map(Automation.Operator::getNodeId)
                .collect(Collectors.toSet());

        // ── Classify conditions ───────────────────────────────────────────
        // Gate condition: previousNodeRef points to an operator node.
        // Chained trigger condition: previousNodeRef points to another condition node
        //   (NOT the trigger root, NOT an operator) — e.g. node_condition_11 refs
        //   node_condition_5.  These must be evaluated AFTER their parent condition
        //   and only when the parent passed.
        // Trigger root condition: previousNodeRef points to the trigger node (or has no ref).

        Set<String> conditionNodeIds = conditions.stream()
                .map(Automation.Condition::getNodeId)
                .collect(Collectors.toSet());

        // topological sort: root triggers first, then chained, then gates
        List<Automation.Condition> rootTriggerConditions = new ArrayList<>();
        List<Automation.Condition> chainedConditions = new ArrayList<>();
        List<Automation.Condition> gateConditions = new ArrayList<>();

        for (Automation.Condition c : conditions) {
            if (!c.isEnabled()) continue;
            if (isGateCondition(c, operatorIds)) {
                gateConditions.add(c);
            } else if (isChainedCondition(c, conditionNodeIds, operatorIds)) {
                chainedConditions.add(c);
            } else {
                rootTriggerConditions.add(c);
            }
        }

        // ── Phase 1: root trigger conditions ─────────────────────────────
        for (Automation.Condition c : rootTriggerConditions) {
            boolean wasActive = cache != null
                    && (cache.getState() == AutomationState.ACTIVE
                    || cache.getState() == AutomationState.HOLDING);
            boolean result = evaluateCondition(automation, c, payload, wasActive);
            NodeResult nr = new NodeResult(c.getNodeId(), result);
            nr.getContributors().add(c.getNodeId());
            ctx.put(nr);
            log.debug("📊 [{}] Trigger condition '{}' ({} {}) → {}",
                    automation.getName(), c.getNodeId(), c.getCondition(),
                    c.getTriggerKey() != null ? c.getTriggerKey() : "scheduled", result);
        }

        // ── Phase 1b: chained trigger conditions (parent-aware) ───────────
        // A chained condition is only evaluated if its parent condition passed.
        // This preserves the graph dependency: node_condition_11 refs
        // node_condition_5, so it only runs when node_condition_5 is true.
        for (Automation.Condition c : chainedConditions) {
            boolean parentTrue = c.getPreviousNodeRef().stream()
                    .map(ref -> ctx.get(ref.getNodeId()))
                    .filter(Objects::nonNull)
                    .anyMatch(NodeResult::isTrue);

            boolean result = false;
            if (parentTrue) {
                boolean wasActive = cache != null
                        && (cache.getState() == AutomationState.ACTIVE
                        || cache.getState() == AutomationState.HOLDING);
                result = evaluateCondition(automation, c, payload, wasActive);
            }
            NodeResult nr = new NodeResult(c.getNodeId(), result);
            if (result) nr.getContributors().add(c.getNodeId());
            ctx.put(nr);
            log.debug("📊 [{}] Chained condition '{}' ({} {}) parentTrue={} → {}",
                    automation.getName(), c.getNodeId(), c.getCondition(),
                    c.getTriggerKey() != null ? c.getTriggerKey() : "scheduled",
                    parentTrue, result);
        }

        // ── Phase 1c: operators ───────────────────────────────────────────
        for (Automation.Operator op : operators) {
            List<NodeResult> inputs = op.getPreviousNodeRef().stream()
                    .map(ref -> ctx.get(ref.getNodeId()))
                    .filter(Objects::nonNull)
                    .toList();

            boolean result;
            Set<String> contributors = new HashSet<>();

            if (inputs.isEmpty()) {
                log.warn("⚠️ Operator '{}' has no resolved inputs — defaulting false", op.getNodeId());
                result = false;
            } else if ("OR".equalsIgnoreCase(op.getLogicType())) {
                result = inputs.stream().anyMatch(NodeResult::isTrue);
                inputs.stream().filter(NodeResult::isTrue)
                        .forEach(n -> contributors.addAll(n.getContributors()));
            } else {
                result = inputs.stream().allMatch(NodeResult::isTrue);
                inputs.forEach(n -> contributors.addAll(n.getContributors()));
            }

            NodeResult opResult = new NodeResult(op.getNodeId(), result);
            opResult.getContributors().addAll(contributors);
            ctx.put(opResult);
            log.debug("📊 [{}] Operator '{}' ({}) → {}",
                    automation.getName(), op.getNodeId(), op.getLogicType(), result);
        }

        // ── Phase 2: gate conditions ──────────────────────────────────────
        for (Automation.Condition c : gateConditions) {
            boolean wasActive = cache != null
                    && (cache.getBranchState(c.getNodeId()) == AutomationState.ACTIVE
                    || cache.getBranchState(c.getNodeId()) == AutomationState.HOLDING);

            boolean parentTrue = c.getPreviousNodeRef().stream()
                    .map(ref -> ctx.get(ref.getNodeId()))
                    .filter(Objects::nonNull)
                    .anyMatch(NodeResult::isTrue);

            boolean result = parentTrue && evaluateCondition(automation, c, payload, wasActive);
            NodeResult nr = new NodeResult(c.getNodeId(), result);
            if (result) nr.getContributors().add(c.getNodeId());
            ctx.put(nr);
            log.debug("📊 [{}] Gate condition '{}' ({}) parentTrue={} → {}",
                    automation.getName(), c.getNodeId(), c.getCondition(), parentTrue, result);
        }

        return findRootResult(automation, ctx);
    }


    // ═════════════════════════════════════════════════════════════════════
    // CONDITION CLASSIFICATION
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Gate condition: its previousNodeRef points to an operator node.
     */
    public boolean isGateCondition(Automation.Condition c, Set<String> operatorIds) {
        return c.getPreviousNodeRef() != null &&
                c.getPreviousNodeRef().stream()
                        .anyMatch(ref -> operatorIds.contains(ref.getNodeId()));
    }

    /**
     * Chained trigger condition: its previousNodeRef points to another CONDITION node
     * (not the trigger root node, not an operator node).
     * Example: node_condition_11 refs node_condition_5.
     * These must be evaluated after their parent condition and inherit its pass/fail.
     */
    public boolean isChainedCondition(Automation.Condition c,
                                      Set<String> conditionNodeIds,
                                      Set<String> operatorIds) {
        if (c.getPreviousNodeRef() == null || c.getPreviousNodeRef().isEmpty()) return false;
        return c.getPreviousNodeRef().stream()
                .anyMatch(ref -> conditionNodeIds.contains(ref.getNodeId())
                        && !operatorIds.contains(ref.getNodeId()));
    }

    /**
     * Returns ALL trigger-side conditions: root triggers + chained triggers.
     * Used by AutomationService to classify which conditions form c1.
     */
    public List<Automation.Condition> getTriggerConditions(Automation automation) {
        Set<String> operatorIds = automation.getOperators() == null ? Set.of() :
                automation.getOperators().stream()
                .map(Automation.Operator::getNodeId)
                .collect(Collectors.toSet());

        Set<String> conditionNodeIds = automation.getConditions() == null ? Set.of() :
                automation.getConditions().stream()
                .map(Automation.Condition::getNodeId)
                .collect(Collectors.toSet());

        return automation.getConditions() == null ? List.of() :
                automation.getConditions().stream()
                .filter(Automation.Condition::isEnabled)
                .filter(c -> !isGateCondition(c, operatorIds))
                .toList(); // includes both root and chained
    }


    // ═════════════════════════════════════════════════════════════════════
    // ACTION RESOLUTION  (kept here for AutomationAbTestService use)
    // ═════════════════════════════════════════════════════════════════════

    public List<Automation.Action> resolveActionsForNode(
            Automation automation,
            ExecutionContext ctx,
            String nodeId,
            String group) {

        Set<String> trueNodes = ctx.getTrueNodes();
        Set<String> falseNodes = ctx.getFalseNodes();

        return automation.getActions().stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsEnabled()))
                .filter(a -> group.equalsIgnoreCase(a.getConditionGroup()))
                .filter(a -> {
                    if (a.getPreviousNodeRef() == null || a.getPreviousNodeRef().isEmpty())
                        return false; // gate-targeted actions must have a ref
                    return a.getPreviousNodeRef().stream().anyMatch(ref -> {
                        if (!ref.getNodeId().equals(nodeId)) return false;
                        String handle = ref.getHandle();
                        if (handle != null && handle.contains("cond-negative"))
                            return falseNodes.contains(ref.getNodeId());
                        return trueNodes.contains(ref.getNodeId())
                                || (handle == null); // no handle = include always
                    });
                })
                .sorted(Comparator.comparingInt(a -> a.getOrder() != 0 ? a.getOrder() : Integer.MAX_VALUE))
                .toList();
    }

    public List<Automation.Action> resolveActionsForGroup(
            Automation automation, ExecutionContext ctx, String group) {

        Set<String> trueNodes = ctx.getTrueNodes();
        Set<String> falseNodes = ctx.getFalseNodes();

        return automation.getActions().stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsEnabled()))
                .filter(a -> group.equalsIgnoreCase(a.getConditionGroup()))
                .filter(a -> {
                    if (a.getPreviousNodeRef() == null || a.getPreviousNodeRef().isEmpty())
                        return true; // global action
                    return a.getPreviousNodeRef().stream().anyMatch(ref -> {
                        String handle = ref.getHandle();
                        if (handle != null && handle.contains("cond-negative"))
                            return falseNodes.contains(ref.getNodeId());
                        return trueNodes.contains(ref.getNodeId());
                    });
                })
                .sorted(Comparator.comparingInt(a -> a.getOrder() != 0 ? a.getOrder() : Integer.MAX_VALUE))
                .toList();
    }


    // ═════════════════════════════════════════════════════════════════════
    // CONDITION EVALUATION
    // ═════════════════════════════════════════════════════════════════════

    private boolean evaluateCondition(Automation automation, Automation.Condition condition,
                                      Map<String, Object> primaryPayload, boolean wasActive) {
        if ("scheduled".equals(condition.getCondition()))
            return isCurrentTimeWithDailyTracking(automation, condition);

        String condDeviceId = condition.getDeviceId();
        Map<String, Object> payload;

        if (condDeviceId != null && !condDeviceId.isBlank()
                && !condDeviceId.equals(automation.getTrigger().getDeviceId())) {
            // Secondary device — fetch latest from Redis cache
            payload = redisService.getRecentDeviceData(condDeviceId);
            if (payload == null || payload.isEmpty()) {
                // FIX Bug#6: warn so the missing key is observable in logs
                log.warn("⚠️ [{}] Secondary device '{}' has no cached data in Redis — " +
                                "condition '{}' ({}) will return false. " +
                                "Ensure the device is publishing live events.",
                        automation.getName(), condDeviceId,
                        condition.getNodeId(), condition.getCondition());
                return false;
            }
        } else {
            // Primary device — use the incoming payload
            payload = primaryPayload;
        }

        String key = condition.getTriggerKey();
        if (key == null || key.isBlank()) {
            log.warn("⚠️ [{}] Condition '{}' has no triggerKey — returning false",
                    automation.getName(), condition.getNodeId());
            return false;
        }
        if (!payload.containsKey(key)) {
            log.warn("⚠️ [{}] Condition '{}': key '{}' not present in payload (keys: {}) — returning false",
                    automation.getName(), condition.getNodeId(), key, payload.keySet());
            return false;
        }

        String value = payload.get(key).toString();

        if (!value.matches("-?\\d+(\\.\\d+)?"))
            return value.equals(condition.getValue());

        double v = Double.parseDouble(value);

        // FIX Bug#7: percentage-based hysteresis (2% of threshold) instead of hardcoded 5.0
        double buffer = getBuffer(condition);

        if (Boolean.TRUE.equals(condition.getIsExact()))
            return condition.getValue().equals(value);

        return switch (condition.getCondition()) {
            case "equal" -> condition.getValue().equals(value);
            case "above" -> wasActive
                    ? v > (Double.parseDouble(condition.getValue()) - buffer)
                    : v > Double.parseDouble(condition.getValue());
            case "below" -> wasActive
                    ? v < (Double.parseDouble(condition.getValue()) + buffer)
                    : v < Double.parseDouble(condition.getValue());
            case "range" -> {
                double above = Double.parseDouble(condition.getAbove());
                double below = Double.parseDouble(condition.getBelow());
                yield wasActive
                        ? v > (above - buffer) && v < (below + buffer)
                        : v > above && v < below;
            }
            default -> false;
        };
    }

    private static double getBuffer(Automation.Condition condition) {
        double thresholdValue;
        try {
            thresholdValue = switch (condition.getCondition()) {
                case "above", "below", "equal" -> Double.parseDouble(condition.getValue());
                case "range" -> (Double.parseDouble(condition.getAbove())
                        + Double.parseDouble(condition.getBelow())) / 2.0;
                default -> Double.parseDouble(condition.getValue());
            };
        } catch (NumberFormatException e) {
            thresholdValue = 0;
        }
        // 2%, min 1 unit
        return Math.max(1.0, Math.abs(thresholdValue) * 0.02);
    }

    private LocalTime parseTime(String timeText) {
        if (timeText == null || timeText.isBlank()) return null;
        try {
            return LocalTime.parse(timeText.trim(),
                    DateTimeFormatter.ofPattern("HH:mm:ss"));
        } catch (Exception e1) {
            try {
                return LocalTime.parse(timeText.trim(),
                        new DateTimeFormatterBuilder().parseCaseInsensitive()
                                .appendPattern("hh:mm:ss a").toFormatter(Locale.ENGLISH));
            } catch (Exception e2) {
                log.warn("⚠️ Unable to parse time: '{}'", timeText);
                return null;
            }
        }
    }


    // ═════════════════════════════════════════════════════════════════════
    // ROOT RESOLUTION
    // ═════════════════════════════════════════════════════════════════════

    public NodeResult findRootResult(Automation automation, ExecutionContext ctx) {
        List<Automation.Operator> operators = automation.getOperators() == null
                ? List.of() : automation.getOperators();

        Set<String> operatorIds = operators.stream()
                .map(Automation.Operator::getNodeId)
                .collect(Collectors.toSet());

        Set<String> conditionNodeIds = automation.getConditions() == null ? Set.of() :
                automation.getConditions().stream()
                .map(Automation.Condition::getNodeId)
                .collect(Collectors.toSet());

        // Root trigger conditions: not gate, not chained
        List<Automation.Condition> rootTriggerConditions = automation.getConditions() == null
                ? List.of()
                : automation.getConditions().stream()
                  .filter(Automation.Condition::isEnabled)
                  .filter(c -> !isGateCondition(c, operatorIds))
                  .filter(c -> !isChainedCondition(c, conditionNodeIds, operatorIds))
                  .toList();

        // All trigger-side conditions (root + chained) — used for implicit AND
        List<Automation.Condition> allTriggerConditions = automation.getConditions() == null
                ? List.of()
                : automation.getConditions().stream()
                  .filter(Automation.Condition::isEnabled)
                  .filter(c -> !isGateCondition(c, operatorIds))
                  .toList();

        if (!operators.isEmpty()) {
            Set<String> referencedByOtherOps = operators.stream()
                    .flatMap(op -> op.getPreviousNodeRef().stream())
                    .map(NodeRef::getNodeId)
                    .filter(operatorIds::contains)
                    .collect(Collectors.toSet());

            return operators.stream()
                    .filter(op -> !referencedByOtherOps.contains(op.getNodeId()))
                    .map(op -> ctx.get(op.getNodeId()))
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElseGet(() -> ctx.get(operators.get(operators.size() - 1).getNodeId()));
        }

        // FIX: implicit AND covers ALL trigger-side conditions (root + chained),
        // not just root-level ones.  For this automation:
        // allTriggerConditions = [node_condition_5, node_condition_11]
        // root = node_condition_5 AND node_condition_11
        if (allTriggerConditions.size() > 1) {
            boolean allTrue = allTriggerConditions.stream()
                    .map(c -> ctx.get(c.getNodeId()))
                    .filter(Objects::nonNull)
                    .allMatch(NodeResult::isTrue);
            NodeResult synthetic = new NodeResult("root:implicit_and", allTrue);
            ctx.put(synthetic);
            return synthetic;
        }

        if (!allTriggerConditions.isEmpty()) {
            NodeResult nr = ctx.get(allTriggerConditions.getFirst().getNodeId());
            if (nr != null) return nr;
        }

        return new NodeResult("root:empty", false);
    }


    // ═════════════════════════════════════════════════════════════════════
    // SCHEDULE EVALUATION
    // ═════════════════════════════════════════════════════════════════════

    private boolean isCurrentTimeWithDailyTracking(Automation automation,
                                                   Automation.Condition condition) {
        ZoneId istZone = ZoneId.of("Asia/Kolkata");
        ZonedDateTime nowZdt = ZonedDateTime.now(istZone);
        LocalTime current = nowZdt.toLocalTime();

        if (condition.getDays() != null && !condition.getDays().isEmpty()) {
            String dow = nowZdt.getDayOfWeek()
                    .getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)
                    .substring(0, 3);
            dow = dow.substring(0, 1).toUpperCase() + dow.substring(1).toLowerCase();
            if (!condition.getDays().contains("Everyday") && !condition.getDays().contains(dow))
                return false;
        }

        String scheduleType = condition.getScheduleType();

        if ("range".equals(scheduleType)) {
            LocalTime from = parseTime(condition.getFromTime());
            LocalTime to = parseTime(condition.getToTime());
            if (from == null || to == null) return false;
            return from.isBefore(to)
                    ? !current.isBefore(from) && !current.isAfter(to)
                    : !current.isBefore(from) || !current.isAfter(to);
        }

        if ("solar".equals(scheduleType)) {
            LocalTime solarTime = getSunTime(condition.getSolarType());
            if (solarTime == null) return false;
            LocalTime adjusted = solarTime.plusMinutes(condition.getOffsetMinutes());
            return Math.abs(ChronoUnit.MINUTES.between(adjusted, current)) <= 3
                    && checkAndSetDailyLock(automation, nowZdt);
        }

        if ("interval".equals(scheduleType)) {
            String intervalKey = "INTERVAL:" + automation.getId() + ":" + condition.getNodeId();
            String runKey = "RUNNING:" + automation.getId() + ":" + condition.getNodeId();
            if (redisService.exists(runKey)) return true;
            if (!redisService.exists(intervalKey)) {
                redisService.setWithExpiry(intervalKey, "run",
                        condition.getIntervalMinutes() * 60L);
                return true;
            }
            return false;
        }

        // "at" / exact time
        LocalTime target = parseTime(condition.getTime());
        if (target == null) return false;
        if (Math.abs(ChronoUnit.MINUTES.between(target, current)) > 1) return false;

        LocalDate today = nowZdt.toLocalDate();
        String dailyKey = String.format("DAILY_FIRE:%s:%s", automation.getId(), today);
        if (redisService.exists(dailyKey)) return false;

        long secondsUntilMidnight = ChronoUnit.SECONDS.between(
                nowZdt, nowZdt.plusDays(1).truncatedTo(ChronoUnit.DAYS));
        redisService.setWithExpiry(dailyKey, "fired", secondsUntilMidnight);
        return true;
    }

    private boolean checkAndSetDailyLock(Automation automation, ZonedDateTime nowZdt) {
        LocalDate today = nowZdt.toLocalDate();
        String key = String.format("DAILY_SOLAR:%s:%s", automation.getId(), today);
        if (redisService.exists(key)) return false;
        long ttl = ChronoUnit.SECONDS.between(nowZdt,
                nowZdt.plusDays(1).truncatedTo(ChronoUnit.DAYS));
        redisService.setWithExpiry(key, "done", ttl);
        return true;
    }

    private LocalTime getSunTime(String solarType) {
        try {
            ZoneId istZone = ZoneId.of("Asia/Kolkata");
            LocalDate today = LocalDate.now(istZone);
            String cacheKey = "SUN_TIME:" + solarType + "-" + today;
            Object cached = redisService.get(cacheKey);
            if (cached != null) return LocalTime.parse(cached.toString());

            Map<String, Object> response = new RestTemplate().getForObject(
                    "https://api.sunrise-sunset.org/json?lat=" + LOCATION_LAT
                            + "&lng=" + LOCATION_LONG + "&formatted=0", Map.class);
            if (response == null || !response.containsKey("results")) return null;

            @SuppressWarnings("unchecked")
            Map<String, String> results = (Map<String, String>) response.get("results");
            String timeStr = "sunrise".equalsIgnoreCase(solarType)
                    ? results.get("sunrise") : results.get("sunset");
            if (timeStr == null) return null;

            LocalTime result = ZonedDateTime.parse(timeStr)
                    .withZoneSameInstant(istZone).toLocalTime();
            redisService.setWithExpiry(cacheKey, result.toString(), 86400);
            return result;
        } catch (Exception e) {
            log.error("❌ Sun time fetch failed: {}", e.getMessage());
            return null;
        }
    }


    // ═════════════════════════════════════════════════════════════════════
    // CONDITION RESULT BUILDER
    // ═════════════════════════════════════════════════════════════════════

    private List<AutomationLog.ConditionResult> buildConditionResults(
            Automation automation, ExecutionContext ctx, Map<String, Object> payload) {

        List<AutomationLog.ConditionResult> results = new ArrayList<>();

        Set<String> operatorIds = automation.getOperators() == null ? Set.of() :
                automation.getOperators().stream()
                .map(Automation.Operator::getNodeId).collect(Collectors.toSet());

        for (Automation.Condition c : automation.getConditions()) {
            NodeResult nr = ctx.get(c.getNodeId());
            if (nr == null) continue;

            String parentOpId = null;
            if (c.getPreviousNodeRef() != null) {
                parentOpId = c.getPreviousNodeRef().stream()
                        .map(NodeRef::getNodeId)
                        .filter(operatorIds::contains)
                        .findFirst().orElse(null);
            }

            var builder = AutomationLog.ConditionResult.builder()
                    .conditionNodeId(c.getNodeId())
                    .conditionType(c.getCondition())
                    .triggerKey(c.getTriggerKey())
                    .passed(nr.isTrue())
                    .isGateCondition(parentOpId != null)
                    .operatorNodeId(parentOpId);

            if ("scheduled".equals(c.getCondition())) {
                String schedType = c.getScheduleType() != null ? c.getScheduleType() : "exact";
                String expected = switch (schedType) {
                    case "range" -> c.getFromTime() + " – " + c.getToTime();
                    case "solar" -> c.getSolarType() + " +" + c.getOffsetMinutes() + "min";
                    case "interval" -> "every " + c.getIntervalMinutes() + "min";
                    default -> c.getTime();
                };
                builder.conditionType("scheduled/" + schedType)
                        .triggerKey("schedule")
                        .expectedValue(expected)
                        .actualValue(LocalTime.now(ZoneId.of("Asia/Kolkata")).toString())
                        .detail(nr.isTrue() ? "Schedule matched" : "Outside schedule window")
                        .days(c.getDays());

            } else {
                // Resolve which payload to show in the log (primary vs secondary)
                Map<String, Object> condPayload = payload;
                if (c.getDeviceId() != null && !c.getDeviceId().isBlank()
                        && !c.getDeviceId().equals(automation.getTrigger().getDeviceId())) {
                    Map<String, Object> secondaryData =
                            redisService.getRecentDeviceData(c.getDeviceId());
                    if (secondaryData != null) condPayload = secondaryData;
                }

                if (c.getTriggerKey() != null && condPayload.containsKey(c.getTriggerKey())) {
                    String raw = condPayload.get(c.getTriggerKey()).toString();
                    String expected = switch (c.getCondition()) {
                        case "above" -> "> " + c.getValue();
                        case "below" -> "< " + c.getValue();
                        case "range" -> c.getAbove() + " < x < " + c.getBelow();
                        default -> c.getValue();
                    };
                    String detail = switch (c.getCondition()) {
                        case "above" -> raw + " > " + c.getValue()
                                + " → " + (nr.isTrue() ? "PASS" : "FAIL");
                        case "below" -> raw + " < " + c.getValue()
                                + " → " + (nr.isTrue() ? "PASS" : "FAIL");
                        case "range" -> raw + " in (" + c.getAbove() + ", " + c.getBelow() + ")"
                                + " → " + (nr.isTrue() ? "PASS" : "FAIL");
                        default -> raw + " == " + c.getValue()
                                + " → " + (nr.isTrue() ? "PASS" : "FAIL");
                    };
                    builder.actualValue(raw).expectedValue(expected).detail(detail);
                } else {
                    builder.actualValue("missing").expectedValue(c.getValue())
                            .detail("Key '" + c.getTriggerKey() + "' not present in payload");
                }
            }
            results.add(builder.build());
        }

        // Operator nodes
        if (automation.getOperators() != null) {
            for (Automation.Operator op : automation.getOperators()) {
                NodeResult nr = ctx.get(op.getNodeId());
                if (nr == null) continue;

                List<String> inputIds = op.getPreviousNodeRef().stream()
                        .map(NodeRef::getNodeId).toList();
                List<String> inputResults = inputIds.stream()
                        .map(id -> id + "=" + (ctx.get(id) != null ? ctx.get(id).isTrue() : "?"))
                        .toList();

                results.add(AutomationLog.ConditionResult.builder()
                        .conditionNodeId(op.getNodeId())
                        .conditionType("operator/" + op.getLogicType()
                                + " (priority=" + op.getPriority() + ")")
                        .triggerKey("operator")
                        .passed(nr.isTrue())
                        .expectedValue(op.getLogicType() + "(" + String.join(", ", inputIds) + ")")
                        .actualValue(String.join(", ", inputResults))
                        .detail(op.getLogicType() + "([" + String.join(", ", inputResults) + "]) → "
                                + (nr.isTrue() ? "TRUE" : "FALSE"))
                        .isGateCondition(false)
                        .build());
            }
        }
        return results;
    }

    /**
     * Public wrapper for buildConditionResults — used by AutomationAbTestService
     * to build condition result sets for variant B without duplicating logic.
     */
    public List<AutomationLog.ConditionResult> buildConditionResultsPublic(
            Automation automation, ExecutionContext ctx, Map<String, Object> payload) {
        return buildConditionResults(automation, ctx, payload);
    }


    // ═════════════════════════════════════════════════════════════════════
    // HUMAN-READABLE HELPERS
    // ═════════════════════════════════════════════════════════════════════

    public String describeCondition(Automation.Condition c) {
        if ("scheduled".equals(c.getCondition())) {
            String schedType = c.getScheduleType() != null ? c.getScheduleType() : "at";
            return switch (schedType) {
                case "range" -> "Time window " + fmtTime(c.getFromTime()) + "-" + fmtTime(c.getToTime());
                case "solar" -> capitalize(c.getSolarType())
                        + (c.getOffsetMinutes() != 0 ? " +" + c.getOffsetMinutes() + " min" : "");
                case "interval" -> "Every " + c.getIntervalMinutes() + " min"
                        + (c.getDurationMinutes() > 0
                        ? " (active for " + c.getDurationMinutes() + " min)" : "");
                default -> "At " + fmtTime(c.getTime());
            };
        }
        String key = c.getTriggerKey() != null ? c.getTriggerKey() : "value";
        return switch (c.getCondition()) {
            case "above" -> key + " > " + c.getValue();
            case "below" -> key + " < " + c.getValue();
            case "range" -> key + " in " + c.getAbove() + "-" + c.getBelow();
            default -> key + " = " + c.getValue();
        };
    }

    String actionSummary(List<Automation.Action> actions) {
        if (actions == null || actions.isEmpty()) return "no actions";
        return actions.stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsEnabled()))
                .map(a -> resolveDeviceLabel(a.getDeviceId(), a.getName())
                        + " " + a.getKey() + "=" + a.getData())
                .collect(Collectors.joining(", "));
    }

    public String resolveDeviceLabel(String deviceId, String actionName) {
        try {
            Device d = mainService.getDevice(deviceId);
            if (d != null && d.getName() != null && !d.getName().isBlank())
                return d.getName();
        } catch (Exception ignored) {
        }
        return (actionName != null && !actionName.isBlank()) ? actionName : deviceId;
    }

    private String fmtTime(String raw) {
        if (raw == null || raw.isBlank()) return "?";
        return raw.replaceAll(":\\d{2}(\\s*[AaPp][Mm])?$", "$1").trim();
    }

    private String capitalize(String s) {
        if (s == null || s.isBlank()) return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }
}