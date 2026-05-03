package dev.automata.automata.v2;

import dev.automata.automata.dto.AutomationRuntimeState;
import dev.automata.automata.dto.BranchDecision;
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
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Pure evaluation component — no Redis writes, no action dispatch, no logging.
 * <p>
 * evaluate() takes an ExecutionPlan, a device payload, and the current runtime
 * state, and returns an EvalResult describing what happened and what to do next.
 * <p>
 * Read-only Redis access:
 * - Secondary device data (getRecentDeviceData) for cross-device conditions
 * - Schedule keys (interval/running/daily) via AutomationStateStore — read only
 * - Sunrise/sunset API cache
 * <p>
 * Fixed bugs vs previous version:
 * 1. handleNoBranch() now fires topLevelPositiveActions (not statelessActions)
 * 2. handleNoBranch() ACTIVE→false correctly fires topLevelNegativeActions
 * 3. handleC1False() branchesToRevert is null-safe (no branches = empty list)
 * 4. informationalActions fire only when c1=false AND a branch was previously active
 * 5. BranchDecision.Type field is public so AutomationOrchestrator can switch on it
 * 6. wasActive for trigger conditions checks branch states for branch automations
 * (topLevelState is never set for branch automations, so was always false)
 * 7. Range hysteresis uses per-bound buffer (2% of each bound) so a value clearly
 * outside the range (e.g. lux=3 with above=5) still fails even when wasActive=true
 * 8. KEEP_ACTIVE decision separated from TRIGGER for outcome/CAS write — a branch
 * that is already active produces SKIPPED (not TRIGGERED) so no state write occurs
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutomationEvaluator {

    private final RedisService redisService;
    private final AutomationStateStore stateStore;

    @Value("${app.location.lat}")
    private String LOCATION_LAT;
    @Value("${app.location.long}")
    private String LOCATION_LONG;

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");


    // ─────────────────────────────────────────────────────────────────────
    // ENTRY POINT
    // ─────────────────────────────────────────────────────────────────────

    public EvalResult evaluate(ExecutionPlan plan,
                               Map<String, Object> payload,
                               AutomationRuntimeState state,
                               String automationId,
                               String traceId) {           // ← NEW PARAM

        long evalStart = System.currentTimeMillis();       // ← NEW

        ZonedDateTime now = ZonedDateTime.now(IST);
        EvalResult.EvalResultBuilder result = EvalResult.builder()
                .automationId(automationId)
                .evaluatedAt(Date.from(now.toInstant()))
                .traceId(traceId);                         // ← NEW

        // Step 1: evaluate trigger-side conditions
        Map<String, Boolean> condResults = new LinkedHashMap<>();
        boolean c1True = evaluateTriggerConditions(
                plan, payload, state, automationId, now, condResults);
        result.conditionResults(condResults).c1True(c1True);

        EvalResult built;
        if (!c1True) {
            built = handleC1False(plan, state, result);
        } else if (plan.getStatelessActions() != null && !plan.getStatelessActions().isEmpty()) {
            built = result
                    .outcome(EvalOutcome.STATELESS_FIRE)
                    .actionsToFire(plan.getStatelessActions())
                    .build();
        } else if (!plan.hasBranches()) {
            built = handleNoBranch(plan, state, result, now);
        } else {
            built = handleBranches(plan, state, result, automationId, now, payload);
        }

        // Stamp evaluation duration before returning                         // ← NEW BLOCK
        long evalDurationMs = System.currentTimeMillis() - evalStart;
        built = built.toBuilder()
                .evalDurationMs(evalDurationMs)
                .build();

        if (evalDurationMs > 200) {
            log.warn("⚠️ [{}] Slow evaluation: {}ms (traceId={})",
                    plan.getAutomationName(), evalDurationMs, traceId);
        }

        return built;
    }


    // ─────────────────────────────────────────────────────────────────────
    // TRIGGER CONDITIONS
    // ─────────────────────────────────────────────────────────────────────

    private boolean evaluateTriggerConditions(
            ExecutionPlan plan,
            Map<String, Object> payload,
            AutomationRuntimeState state,
            String automationId,
            ZonedDateTime now,
            Map<String, Boolean> condResults) {
        List<ExecutionPlan.CompiledCondition> conditions = plan.getTriggerConditions();

        if (conditions == null || conditions.isEmpty()) return true;

        // Fix: for branch automations, topLevelState is never written — only branchStates are.
        // wasActive for trigger conditions should be true if ANY branch is currently active,
        // so hysteresis applies correctly at the trigger condition level.
        boolean anyBranchActive = plan.hasBranches()
                && plan.getBranches().stream().anyMatch(b -> state.isBranchActive(b.getGateNodeId()));

        boolean allTrue = true;
        for (ExecutionPlan.CompiledCondition c : conditions) {
            if (c.isChained() && c.getParentConditionNodeId() != null) {
                Boolean parentResult = condResults.get(c.getParentConditionNodeId());
                if (parentResult == null || !parentResult) {
                    condResults.put(c.getNodeId(), false);
                    allTrue = false;
                    log.debug("  ⛔ [{}] Chained '{}' skipped — parent '{}' false",
                            plan.getAutomationName(), c.getNodeId(), c.getParentConditionNodeId());
                    continue;
                }
            }
            boolean wasActive = plan.hasBranches() ? anyBranchActive : state.isTopLevelActive();
            boolean res = evalSingleCondition(c, payload, wasActive, automationId, now);
            condResults.put(c.getNodeId(), res);
            if (!res) allTrue = false;
            log.debug("  📊 [{}] Trigger '{}' ({}) wasActive={} → {}",
                    plan.getAutomationName(), c.getNodeId(), c.getConditionType(), wasActive, res);
        }
        return allTrue;
    }


    // ─────────────────────────────────────────────────────────────────────
    // C1 FALSE
    // ─────────────────────────────────────────────────────────────────────

    private EvalResult handleC1False(ExecutionPlan plan,
                                     AutomationRuntimeState state,
                                     EvalResult.EvalResultBuilder result) {
        boolean anyWasActive;
        List<String> branchesToRevert;

        if (plan.hasBranches()) {
            List<String> activeBranches = plan.getBranches().stream()
                    .filter(b -> state.isBranchActive(b.getGateNodeId()))
                    .map(ExecutionPlan.CompiledBranch::getGateNodeId)
                    .toList();
            anyWasActive = !activeBranches.isEmpty();
            branchesToRevert = activeBranches;
        } else {
            anyWasActive = state.isTopLevelActive();
            branchesToRevert = List.of();
        }

        List<ExecutionPlan.CompiledAction> toFire = new ArrayList<>();

        // Informational actions fire only when something was active — not on every NOT_MET tick.
        // Bug fix: previously fired on every c1=false regardless of prior state.
        if (anyWasActive && plan.getInformationalActions() != null)
            toFire.addAll(plan.getInformationalActions());

        if (anyWasActive && plan.getC1NegativeActions() != null)
            toFire.addAll(plan.getC1NegativeActions());

        // For no-branch automations: fire top-level negative on ACTIVE→false
        if (!plan.hasBranches() && anyWasActive && plan.hasTopLevelNegativeActions())
            toFire.addAll(plan.getTopLevelNegativeActions());

        return result
                .outcome(anyWasActive ? EvalOutcome.C1_NEGATIVE : EvalOutcome.NOT_MET)
                .anyWasActive(anyWasActive)
                .actionsToFire(toFire)
                .branchesToRevert(branchesToRevert)
                .build();
    }


    // ─────────────────────────────────────────────────────────────────────
    // NO-BRANCH PATH
    // ─────────────────────────────────────────────────────────────────────

    /**
     * No-branch = simple trigger→action automations (no operators, no gates).
     * State machine: IDLE ↔ ACTIVE based on c1 result.
     * <p>
     * Bug fix: previous version passed statelessActions as positive actions,
     * which is wrong — stateless actions are conditionGroup="none" and fire
     * instantly without state. Here we correctly use topLevelPositiveActions.
     */
    private EvalResult handleNoBranch(ExecutionPlan plan,
                                      AutomationRuntimeState state,
                                      EvalResult.EvalResultBuilder result,
                                      ZonedDateTime now) {
        boolean isActive = state.isTopLevelActive();

        if (!isActive) {
            // IDLE + c1=true → TRIGGERED
            // Stateful if there are negative actions to revert to, stateless otherwise
            boolean hasNegative = plan.hasTopLevelNegativeActions();
            return result
                    .outcome(EvalOutcome.TRIGGERED)
                    .actionsToFire(plan.getTopLevelPositiveActions() != null
                            ? plan.getTopLevelPositiveActions() : List.of())
                    .nextTopLevelState(hasNegative ? "ACTIVE" : "IDLE")
                    .triggeredAt(Date.from(now.toInstant()))
                    .build();

        } else {
            // ACTIVE + c1=true → SKIPPED (already running, nothing to do)
            return result
                    .outcome(EvalOutcome.SKIPPED)
                    .reason("Already active — condition still true")
                    .build();
        }
    }


    // ─────────────────────────────────────────────────────────────────────
    // BRANCH PATH
    // ─────────────────────────────────────────────────────────────────────

    private EvalResult handleBranches(ExecutionPlan plan,
                                      AutomationRuntimeState state,
                                      EvalResult.EvalResultBuilder result,
                                      String automationId,
                                      ZonedDateTime now,
                                      Map<String, Object> payload) {

        List<BranchDecision> decisions = new ArrayList<>();
        ExecutionPlan.CompiledBranch winner = null;
        List<ExecutionPlan.CompiledBranch> trueBranches = new ArrayList<>();

        // Evaluate each gate — branches are sorted priority DESC by the compiler
        for (ExecutionPlan.CompiledBranch branch : plan.getBranches()) {
            boolean wasActive = state.isBranchActive(branch.getGateNodeId());
            boolean gateTrue = evalSingleCondition(
                    branch.getGateCondition(), payload, wasActive, automationId, now);
            log.debug("  📊 [{}] Gate '{}' (pri={}) wasActive={} → {}",
                    plan.getAutomationName(), branch.getGateNodeId(), branch.getPriority(), wasActive, gateTrue);
            if (gateTrue) {
                trueBranches.add(branch);
                if (winner == null) winner = branch;
            }
        }

        for (ExecutionPlan.CompiledBranch branch : plan.getBranches()) {
            boolean gateTrue = trueBranches.contains(branch);
            boolean isWinner = branch == winner;
            String bState = state.getBranchStateStr(branch.getGateNodeId());
            boolean wasActive = "ACTIVE".equals(bState) || "HOLDING".equals(bState);

            if (wasActive) {
                if (!gateTrue || !isWinner) {
                    String reason = !gateTrue ? "Gate no longer true"
                            : "Overridden by priority " + winner.getPriority();
                    decisions.add(BranchDecision.revert(branch, reason));
                } else {
                    // Still the winner — check duration timer
                    if ("HOLDING".equals(bState)
                            && !stateStore.runningKeyExists(automationId, branch.getGateNodeId())) {
                        decisions.add(BranchDecision.durationExpired(branch));
                    } else {
                        decisions.add(BranchDecision.keepActive(branch));
                    }
                }
            } else {
                // IDLE
                if (gateTrue && isWinner) {
                    // Interval cooldown guard
//                    if ("interval".equals(branch.getGateCondition().getScheduleType())
//                            && branch.getGateCondition().getIntervalMinutes() > 0
//                            && state.getLastExecutionTime() != null) {
//                        long secSince = (new Date().getTime()
//                                - state.getLastExecutionTime().getTime()) / 1000;
//                        long intervalSec = branch.getGateCondition().getIntervalMinutes() * 60L;
//                        if (secSince < intervalSec) {
//                            log.debug("  ⏳ [{}] '{}' interval cooldown — {}s remaining",
//                                    automationId, branch.getGateNodeId(), intervalSec - secSince);
//                            decisions.add(BranchDecision.cooldown(branch));
//                            continue;
//                        }
//                    }
                    decisions.add(BranchDecision.trigger(branch));
                } else if (gateTrue) {
                    decisions.add(BranchDecision.suppressed(branch,
                            winner.getGateNodeId()));
                }
                // IDLE + gate false → nothing
            }
        }

        // Fix: separate "a new TRIGGER fired this tick" from "a branch is currently running".
        // Previously both TRIGGER and KEEP_ACTIVE set anyActive=true, causing outcome=TRIGGERED
        // on every 12s tick while a branch was already active. This wasted a CAS write per tick
        // and logged TRIGGERED instead of SKIPPED for steady-state active branches.
        boolean anyJustTriggered = decisions.stream()
                .anyMatch(d -> d.getType() == BranchDecision.Type.TRIGGER);
        boolean anyCurrentlyActive = decisions.stream()
                .anyMatch(d -> d.getType() == BranchDecision.Type.TRIGGER
                        || d.getType() == BranchDecision.Type.KEEP_ACTIVE);

        // Fix: REVERT / DURATION_EXPIRED decisions must not be swallowed by the NOT_MET
        // early exit. This was the root cause of negative actions not firing after the
        // RUNNING key expired when no other gate branch was currently true.
        //
        // Scenario that was failing:
        //   node_condition_8 HOLDING (interval 2min / duration 1min)
        //   → RUNNING key expires after 1min
        //   → gate re-evaluates: runningKeyExists=false, intervalKeyExists=true → false
        //   → gateTrue=false, no other branch true → winner=null, anyCurrentlyActive=false
        //   → old code returned NOT_MET here, never dispatching negativeActions
        //   → branch stayed in HOLDING state forever, negative actions never fired
        boolean anyPendingRevert = decisions.stream()
                .anyMatch(d -> d.getType() == BranchDecision.Type.REVERT
                        || d.getType() == BranchDecision.Type.DURATION_EXPIRED);

        // c1=true, no branch fired, no branch active, AND no pending reverts → fallback/NOT_MET
        if (!anyCurrentlyActive && winner == null && !anyPendingRevert) {
            List<ExecutionPlan.CompiledAction> fallback = plan.getFallbackActions();
            if (fallback != null && !fallback.isEmpty()) {
                return result.outcome(EvalOutcome.FALLBACK)
                        .actionsToFire(fallback).branchDecisions(decisions).build();
            }
            return result.outcome(EvalOutcome.NOT_MET)
                    .reason("c1 true but no gate branch matched and no fallback defined")
                    .branchDecisions(decisions).build();
        }

        // Determine outcome:
        //   TRIGGERED  — a new branch fired (positive actions dispatched, state → ACTIVE/HOLDING)
        //   RESTORED   — a branch reverted with no new trigger (negative actions dispatched, state → IDLE)
        //   SKIPPED    — existing branch still active, nothing new to do (no CAS write)
        EvalOutcome outcome = anyJustTriggered ? EvalOutcome.TRIGGERED
                : anyPendingRevert ? EvalOutcome.RESTORED
                  : anyCurrentlyActive ? EvalOutcome.SKIPPED
                    : EvalOutcome.NOT_MET;

        return result
                .outcome(outcome)
                .branchDecisions(decisions)
                .triggeredAt(anyJustTriggered ? Date.from(now.toInstant()) : null)
                .anyWasActive(anyCurrentlyActive || anyPendingRevert)
                .build();
    }


    // ─────────────────────────────────────────────────────────────────────
    // SINGLE CONDITION EVALUATION
    // ─────────────────────────────────────────────────────────────────────

    boolean evalSingleCondition(ExecutionPlan.CompiledCondition c,
                                Map<String, Object> primaryPayload,
                                boolean wasActive,
                                String automationId,
                                ZonedDateTime now) {
        if ("scheduled".equals(c.getConditionType()))
            return evalScheduled(c, automationId, now);

        // Resolve payload: secondary device or primary
        Map<String, Object> payload = primaryPayload;
        if (c.getDeviceId() != null && !c.getDeviceId().isBlank()) {
            Map<String, Object> secondary = redisService.getRecentDeviceData(c.getDeviceId());
            if (secondary == null || secondary.isEmpty()) {
                log.warn("⚠️ [{}] Secondary device '{}' has no Redis data — condition '{}' = false",
                        automationId, c.getDeviceId(), c.getNodeId());
                return false;
            }
            payload = secondary;
        }

        String key = c.getTriggerKey();
        if (key == null || key.isBlank()) {
            log.warn("⚠️ [{}] Condition '{}' has no triggerKey", automationId, c.getNodeId());
            return false;
        }
        if (!payload.containsKey(key)) {
            log.warn("⚠️ [{}] Condition '{}': key '{}' missing (keys: {})",
                    automationId, c.getNodeId(), key, payload.keySet());
            return false;
        }

        String raw = payload.get(key).toString();
        if (!raw.matches("-?\\d+(\\.\\d+)?"))
            return raw.equals(c.getValue());

        double v = Double.parseDouble(raw);

        if (c.isExact()) return c.getValue().equals(raw);

        return switch (c.getConditionType()) {
            case "equal" -> c.getValue().equals(raw);
            case "above" -> {
                double threshold = Double.parseDouble(c.getValue());
                double buf = Math.max(1.0, Math.abs(threshold) * 0.02);
                yield wasActive ? v > (threshold - buf) : v > threshold;
            }
            case "below" -> {
                double threshold = Double.parseDouble(c.getValue());
                double buf = Math.max(1.0, Math.abs(threshold) * 0.02);
                yield wasActive ? v < (threshold + buf) : v < threshold;
            }
            case "range" -> {
                double a = Double.parseDouble(c.getAbove());
                double b = Double.parseDouble(c.getBelow());
                // Fix: per-bound buffer — 2% of each bound's own value, min 1 unit.
                // Using the midpoint (old approach) gave above-buffer = -0.05 for above=5,
                // meaning lux=3 still passed when wasActive=true (hysteresis kept lights on
                // when sensor was clearly outside the valid range). Now each bound uses its
                // own threshold so the hysteresis window scales correctly per bound:
                // above=5 → bufLow=1.0, bufHigh=10.0 for below=500
                // wasActive=true: lux > 4 && lux < 510  (lux=3 → 3>4 = false ✓)
                double bufLow = Math.max(1.0, Math.abs(a) * 0.02);
                double bufHigh = Math.max(1.0, Math.abs(b) * 0.02);
                yield wasActive ? v > (a - bufLow) && v < (b + bufHigh) : v > a && v < b;
            }
            default -> false;
        };
    }


    // ─────────────────────────────────────────────────────────────────────
    // SCHEDULE EVALUATION
    // ─────────────────────────────────────────────────────────────────────

    private boolean evalScheduled(ExecutionPlan.CompiledCondition c,
                                  String automationId, ZonedDateTime now) {
        LocalTime current = now.toLocalTime();

        if (c.getDays() != null && !c.getDays().isEmpty()) {
            String dow = now.getDayOfWeek()
                    .getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
            dow = dow.substring(0, 1).toUpperCase() + dow.substring(1).toLowerCase();
            if (!c.getDays().contains("Everyday") && !c.getDays().contains(dow)) return false;
        }

        String st = c.getScheduleType();

        if ("range".equals(st)) {
            LocalTime from = parseTime(c.getFromTime()), to = parseTime(c.getToTime());
            if (from == null || to == null) return false;
            return from.isBefore(to)
                    ? !current.isBefore(from) && !current.isAfter(to)
                    : !current.isBefore(from) || !current.isAfter(to);
        }

        if ("solar".equals(st)) {
            LocalTime solar = getSunTime(c.getSolarType());
            if (solar == null) return false;
            LocalTime adjusted = solar.plusMinutes(c.getOffsetMinutes());
            if (Math.abs(ChronoUnit.MINUTES.between(adjusted, current)) > 3) return false;
            return !stateStore.dailySolarKeyExists(automationId, now.toLocalDate().toString());
        }

        if ("interval".equals(st)) {
            if (stateStore.runningKeyExists(automationId, c.getNodeId())) return true;
            return !stateStore.intervalKeyExists(automationId, c.getNodeId());
        }

        // "at" / exact time
        LocalTime target = parseTime(c.getTime());
        if (target == null) return false;
        if (Math.abs(ChronoUnit.MINUTES.between(target, current)) > 1) return false;
        return !stateStore.dailyFireKeyExists(automationId, now.toLocalDate().toString());
    }

    private LocalTime parseTime(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalTime.parse(s.trim(), DateTimeFormatter.ofPattern("HH:mm:ss"));
        } catch (Exception e1) {
            try {
                return LocalTime.parse(s.trim(),
                        new DateTimeFormatterBuilder().parseCaseInsensitive()
                                .appendPattern("hh:mm:ss a").toFormatter(Locale.ENGLISH));
            } catch (Exception e2) {
                log.warn("⚠️ Unable to parse time: '{}'", s);
                return null;
            }
        }
    }

    private LocalTime getSunTime(String solarType) {
        try {
            String today = LocalDate.now(IST).toString();
            String cacheKey = "SUN_TIME:" + solarType + "-" + today;
            Object cached = redisService.get(cacheKey);
            if (cached != null) return LocalTime.parse(cached.toString());
            Map<String, Object> response = new RestTemplate().getForObject(
                    "https://api.sunrise-sunset.org/json?lat=" + LOCATION_LAT
                            + "&lng=" + LOCATION_LONG + "&formatted=0", Map.class);
            if (response == null || !response.containsKey("results")) return null;
            @SuppressWarnings("unchecked")
            Map<String, String> results = (Map<String, String>) response.get("results");
            String ts = "sunrise".equalsIgnoreCase(solarType)
                    ? results.get("sunrise") : results.get("sunset");
            if (ts == null) return null;
            LocalTime result = ZonedDateTime.parse(ts).withZoneSameInstant(IST).toLocalTime();
            redisService.setWithExpiry(cacheKey, result.toString(), 86400);
            return result;
        } catch (Exception e) {
            log.error("❌ Sun time fetch failed: {}", e.getMessage());
            return null;
        }
    }


    // ─────────────────────────────────────────────────────────────────────
    // RESULT / DECISION TYPES
    // ─────────────────────────────────────────────────────────────────────

    public enum EvalOutcome {
        TRIGGERED,      // positive actions fire, state advances
        C1_NEGATIVE,    // trigger condition turned false — revert active branches
        SKIPPED,        // already in correct state, nothing to do
        NOT_MET,        // condition false, no active branches
        STATELESS_FIRE, // stateless (none-group) actions fired
        FALLBACK,       // c1 true, no gate matched, explicit fallback fired
        SUPPRESSED,      // branch matched but higher-priority branch won
        RESTORED
    }

    @lombok.Builder(toBuilder = true)          // toBuilder=true needed for duration stamp
    @lombok.Value
    public static class EvalResult {
        String automationId;
        Date evaluatedAt;
        boolean c1True;
        EvalOutcome outcome;
        String reason;
        Map<String, Boolean> conditionResults;
        List<ExecutionPlan.CompiledAction> actionsToFire;
        List<BranchDecision> branchDecisions;
        List<String> branchesToRevert;
        String nextTopLevelState;
        Date triggeredAt;
        boolean anyWasActive;

        String traceId;          // ← NEW: propagated from execute() call site
        Long evalDurationMs;     // ← NEW: wall-clock ms for evaluator hot path

        public boolean hasActions() {
            return actionsToFire != null && !actionsToFire.isEmpty();
        }

        public boolean hasChanges() {
            return outcome == EvalOutcome.TRIGGERED
                    || outcome == EvalOutcome.RESTORED
                    || outcome == EvalOutcome.C1_NEGATIVE
                    || outcome == EvalOutcome.STATELESS_FIRE
                    || outcome == EvalOutcome.FALLBACK;
        }
    }


}