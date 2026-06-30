package dev.automata.automata.v2;

import dev.automata.automata.dto.AutomationRuntimeState;
import dev.automata.automata.dto.ConditionMemory;
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
import java.util.stream.Stream;

/**
 * Pure evaluation component — NO Redis writes, NO action dispatch.
 *
 * <p>Bug fixes (this version)
 * ─────────────────────────
 * BUG 4 — OR fanout branch actions silently swallowed when top-level already ACTIVE
 * <p>
 * Root cause: walkConditionTree() previously routed every successful tree walk
 * through handleActivate(), which gates on state.isTopLevelActive(). When an
 * automation is already ACTIVE (because another OR branch fired earlier), any
 * subsequent OR branch that becomes true gets EvalOutcome.SKIPPED — its own
 * positiveActions are never dispatched and the EvalResult.hasChanges() == false
 * short-circuit in the orchestrator causes a full early return with zero side
 * effects.
 * <p>
 * Concrete example from the TESTING automation:
 * node_condition_22 (9:30 AM–4:30 PM) is a sibling OR branch to
 * node_condition_10 (6 PM–2 AM) under the same OR fanout at node_18.
 * If the automation was triggered via the 6 PM branch and later the
 * daytime window opens (9:30 AM), node_22 passes but handleActivate()
 * sees isTopLevelActive()==true and returns SKIPPED with no actions.
 * <p>
 * Fix: walkConditionTree() now distinguishes two cases after a successful walk:
 * <ol>
 *   <li>The walk produced branch-level positiveActions (walkResult.positiveActionsToFire
 *       is non-empty) — these belong to specific OR fanout branches. We check per-NODE
 *       active state instead of the top-level state: if any of the PASSED nodes
 *       were previously INACTIVE, those nodes have just transitioned inactive→active
 *       and their actions must fire. This produces EvalOutcome.BRANCH_TRIGGERED.
 *       If every passed node was already ACTIVE, we emit EvalOutcome.SKIPPED so
 *       the orchestrator's hasChanges() guard correctly suppresses re-dispatch.</li>
 *   <li>The walk produced NO branch-level actions (pure condition chain with only
 *       top-level actions). Behaviour is unchanged: delegate to handleActivate()
 *       which checks top-level state and returns TRIGGERED or SKIPPED as before.</li>
 * </ol>
 * EvalOutcome.BRANCH_TRIGGERED is treated identically to TRIGGERED in the
 * orchestrator's dispatch and state-compute paths, with the sole difference that
 * it does NOT touch the top-level topLevelState field (it may already be ACTIVE
 * and should remain so; the per-node nodeStates are the authoritative record).
 * <p>
 * Previously documented bug fixes (carried forward unchanged):
 * BUG 1 — Stranded descendants (see class javadoc in previous version)
 * BUG 2 — durationMinutes silently ignored (intervalNodesToArm)
 * BUG 3 — Cross-branch state pollution (conditionResults per-node flags)
 * Performance fix — SECONDARY_CACHE per-evaluation in-memory cache
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutomationEvaluator {

    private final RedisService redisService;
    private final AutomationStateStore stateStore;
    private final MainService mainService;

    @Value("${app.location.lat}")
    private String LOCATION_LAT;
    @Value("${app.location.long}")
    private String LOCATION_LONG;

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    /**
     * Per-evaluation in-memory cache for secondary device data.
     * Keyed by deviceId → payload map. Created fresh for each evaluate() call.
     */
    private static final ThreadLocal<Map<String, Map<String, Object>>> SECONDARY_CACHE =
            ThreadLocal.withInitial(HashMap::new);


    // ─────────────────────────────────────────────────────────────────────
    // ENTRY POINT
    // ─────────────────────────────────────────────────────────────────────

    public EvalResult evaluate(ExecutionPlan plan,
                               Map<String, Object> payload,
                               AutomationRuntimeState state,
                               String automationId,
                               String traceId) {

        long evalStart = System.currentTimeMillis();
        ZonedDateTime now = ZonedDateTime.now(IST);

        SECONDARY_CACHE.get().clear();

        EvalResult.EvalResultBuilder result = EvalResult.builder()
                .automationId(automationId)
                .evaluatedAt(Date.from(now.toInstant()))
                .traceId(traceId);

        Map<String, ConditionMemory> memoryUpdates = new LinkedHashMap<>();

        EvalResult built;

        if (plan.getStatelessActions() != null && !plan.getStatelessActions().isEmpty()) {
            built = result
                    .outcome(EvalOutcome.STATELESS_FIRE)
                    .actionsToFire(plan.getStatelessActions())
                    .c1True(true)
                    .build();
        } else if (plan.hasConditionTree()) {
            built = walkConditionTree(plan, payload, state, automationId, now, result, memoryUpdates);
        } else {
            built = handleActivate(plan, state, result.c1True(true), now);
        }

        built = built.toBuilder()
                .memoryUpdates(memoryUpdates)
                .evalDurationMs(System.currentTimeMillis() - evalStart)
                .build();

        if (built.getEvalDurationMs() > 200)
            log.warn("⚠️ [{}] Slow evaluation: {}ms (traceId={})",
                    plan.getAutomationName(), built.getEvalDurationMs(), traceId);

        SECONDARY_CACHE.get().clear();

        return built;
    }


    // ─────────────────────────────────────────────────────────────────────
    // CONDITION TREE WALK
    // ─────────────────────────────────────────────────────────────────────

    private EvalResult walkConditionTree(ExecutionPlan plan,
                                         Map<String, Object> payload,
                                         AutomationRuntimeState state,
                                         String automationId,
                                         ZonedDateTime now,
                                         EvalResult.EvalResultBuilder result,
                                         Map<String, ConditionMemory> memoryUpdates) {

        Map<String, ExecutionPlan.CompiledConditionNode> nodeMap = new LinkedHashMap<>();
        if (plan.getConditionTree() != null)
            plan.getConditionTree().forEach(n -> nodeMap.put(n.getNodeId(), n));

        Set<String> visited = new HashSet<>();
        Map<String, Boolean> allConditionResults = new LinkedHashMap<>();
        Set<String> intervalNodesToArm = new LinkedHashSet<>();

        for (String rootId : plan.getRootConditionNodeIds()) {
            ExecutionPlan.CompiledConditionNode rootNode = nodeMap.get(rootId);
            if (rootNode == null) {
                log.warn("⚠️ Root node '{}' not found in nodeMap for '{}' — skipping",
                        rootId, plan.getAutomationName());
                continue;
            }

            TreeWalkResult walkResult = walkNode(rootNode, nodeMap, payload, state,
                    automationId, now, plan.getAutomationName(), memoryUpdates, visited,
                    intervalNodesToArm);

            allConditionResults.putAll(walkResult.conditionResults);

            if (!walkResult.passed) {
                log.debug("🌿 [{}] Tree walk failed at '{}' — {} negative action(s)",
                        plan.getAutomationName(), walkResult.failedNodeId,
                        walkResult.negativeActionsToFire.size());

                boolean anyNodeWasActive = plan.getConditionTree().stream()
                        .anyMatch(n -> n.isStateful() && state.isNodeActive(n.getNodeId()));

                List<ExecutionPlan.CompiledAction> toFire = new ArrayList<>();
                if (anyNodeWasActive && plan.getInformationalActions() != null)
                    toFire.addAll(plan.getInformationalActions());
                toFire.addAll(walkResult.negativeActionsToFire);

                return result
                        .c1True(false)
                        .outcome(anyNodeWasActive ? EvalOutcome.C1_NEGATIVE : EvalOutcome.NOT_MET)
                        .actionsToFire(toFire)
                        .anyWasActive(anyNodeWasActive)
                        .conditionResults(allConditionResults)
                        .intervalNodesToArm(Set.of())
                        .build();
            }

            // ── BUG 4 FIX: branch-level vs top-level activation ───────────────
            //
            // If the walk produced per-branch positive actions we must NOT route
            // through handleActivate() unconditionally.  handleActivate() gates on
            // the top-level isTopLevelActive() flag, which is shared across all OR
            // branches — so once ANY branch fires and sets the automation ACTIVE,
            // every subsequent branch whose window opens gets SKIPPED and its own
            // positiveActions are silently dropped.
            //
            // Instead:
            //   • If walkResult has its own positiveActionsToFire → use per-node
            //     state to decide whether this is a new activation (BRANCH_TRIGGERED)
            //     or a steady-state repeat (SKIPPED).
            //   • If walkResult has NO branch-level actions → fall through to the
            //     original handleActivate() path (top-level TRIGGERED / SKIPPED).
            //
            // "Any passed node was previously INACTIVE" is the correct transition
            // signal: it means at least one OR branch just changed state and its
            // hardware commands must be dispatched.

            List<ExecutionPlan.CompiledAction> branchActions = walkResult.positiveActionsToFire;

            if (branchActions != null && !branchActions.isEmpty()) {
                // Determine which nodes passed this tick so we can check their
                // previous per-node state.
                Set<String> passedNodeIds = new LinkedHashSet<>();
                allConditionResults.forEach((nodeId, passed) -> {
                    if (Boolean.TRUE.equals(passed)) passedNodeIds.add(nodeId);
                });

                boolean anyBranchJustActivated = passedNodeIds.stream()
                        .anyMatch(nodeId -> {
                            ExecutionPlan.CompiledConditionNode n = nodeMap.get(nodeId);
                            // Only stateful nodes track per-node active state; for non-stateful
                            // nodes we always treat them as "just activated" so their actions fire.
                            return n == null || !n.isStateful() || !state.isNodeActive(nodeId);
                        });

                log.debug("🌿 [{}] Branch walk passed — {} branch action(s), anyJustActivated={}",
                        plan.getAutomationName(), branchActions.size(), anyBranchJustActivated);

                if (anyBranchJustActivated) {
                    // At least one OR branch node transitioned inactive → active.
                    // Dispatch its positive actions and record the activation.
                    EvalResult activated = result
                            .c1True(true)
                            .outcome(EvalOutcome.BRANCH_TRIGGERED)
                            .actionsToFire(branchActions)
                            .conditionResults(allConditionResults)
                            .nextTopLevelState("ACTIVE")
                            .triggeredAt(Date.from(now.toInstant()))
                            .build();
                    return activated.toBuilder().intervalNodesToArm(intervalNodesToArm).build();
                } else {
                    // Every passed node was already ACTIVE — steady state, no re-dispatch.
                    return result
                            .c1True(true)
                            .outcome(EvalOutcome.SKIPPED)
                            .reason("Branch already active — OR branch still true")
                            .conditionResults(allConditionResults)
                            .intervalNodesToArm(Set.of())
                            .build();
                }
            }

            // No branch-level actions: original top-level handleActivate() path.
            EvalResult activated = handleActivate(plan, state,
                    result.c1True(true)
                            .conditionResults(allConditionResults)
                            .actionsToFire(walkResult.positiveActionsToFire),
                    now);
            return activated.toBuilder().intervalNodesToArm(intervalNodesToArm).build();
        }

        // No root nodes evaluated
        return handleActivate(plan, state, result.c1True(true), now)
                .toBuilder().intervalNodesToArm(Set.of()).build();
    }


    private TreeWalkResult walkNode(ExecutionPlan.CompiledConditionNode node,
                                    Map<String, ExecutionPlan.CompiledConditionNode> nodeMap,
                                    Map<String, Object> payload,
                                    AutomationRuntimeState state,
                                    String automationId,
                                    ZonedDateTime now,
                                    String automationName,
                                    Map<String, ConditionMemory> memoryUpdates,
                                    Set<String> visited,
                                    Set<String> intervalNodesToArm) {

        // Cycle guard — MUST be first
        if (!visited.add(node.getNodeId())) {
            log.warn("⚠️ [{}] Cycle detected at node '{}' — skipping", automationName, node.getNodeId());
            return TreeWalkResult.passed(List.of(), Map.of());
        }

        boolean wasActive = node.isStateful() && state.isNodeActive(node.getNodeId());

        boolean rawResult = evalSingleCondition(node.getCondition(), payload,
                wasActive, automationId, now);

        // ── Apply memory policy ────────────────────────────────────────────
        boolean result;
        if (node.hasMemoryPolicy()) {
            ConditionMemory currentMemory = state.getConditionMemory(node.getNodeId());
            MemoryPolicyResult policyResult =
                    applyMemoryPolicy(node.getMemoryPolicy(), rawResult, currentMemory, now);
            result = policyResult.passes;
            memoryUpdates.put(node.getNodeId(), policyResult.updatedMemory);
            log.debug("  🧠 [{}] Node '{}' raw={} memory={} → passes={}",
                    automationName, node.getNodeId(), rawResult,
                    policyResult.memorySummary, result);
        } else {
            result = rawResult;
            ConditionMemory currentMemory = state.getConditionMemory(node.getNodeId());
            ConditionMemory updated = rawResult
                    ? currentMemory.withRawTrue(now.toInstant().toEpochMilli())
                    : currentMemory.withRawFalse();
            memoryUpdates.put(node.getNodeId(), updated.withPolicyPassed(rawResult));
        }

        Map<String, Boolean> condResults = new LinkedHashMap<>();
        condResults.put(node.getNodeId(), result);

        log.debug("  📊 [{}] Node '{}' ({}) wasActive={} → {}",
                automationName, node.getNodeId(),
                node.getCondition().getConditionType(), wasActive, result);

        // BUG 2 fix: record interval nodes with a duration window that
        // evaluated true this tick, so the orchestrator can arm RUNNING
        // after a successful dispatch.
        if (result && isIntervalWithDuration(node.getCondition())) {
            intervalNodesToArm.add(node.getNodeId());
        }

        // ---------------------------------------------------------------------
        // Respect runFor (durationMinutes)
        // If the condition is scheduled+interval with a runFor duration and the
        // RUNNING key still exists, keep the node logically TRUE until the
        // duration expires.
        // ---------------------------------------------------------------------
        if (!result
                && isIntervalWithDuration(node.getCondition())
                && stateStore.runningKeyExists(automationId, node.getNodeId())) {

            log.debug("⏳ [{}] Node '{}' still within runFor duration - suppressing negative transition",
                    automationName, node.getNodeId());

            result = true;
            condResults.put(node.getNodeId(), true);
        }
// ── Generic negative-action grace (any condition with durationMinutes>0) ──
        if (hasNegativeGraceDuration(node.getCondition())) {
            long nowMs = now.toInstant().toEpochMilli();
            long durationMs = node.getCondition().getDurationMinutes() * 60_000L;

            if (result) {
                // Condition recovered (or never failed) — clear any stale grace timer
                // so the NEXT false transition starts its own fresh window.
                stateStore.clearGrace(automationId, node.getNodeId());
            } else {
                Long armedAt = stateStore.getGraceArmedAtEpochMs(automationId, node.getNodeId());

                if (armedAt == null) {
                    if (wasActive) {
                        // First false tick after being active — start the grace clock,
                        // and hold this tick as "true" so the negative path doesn't fire yet.
                        stateStore.armGrace(automationId, node.getNodeId(), nowMs,
                                node.getCondition().getDurationMinutes() * 60L + 30);
                        log.info("⏳ [{}] Node '{}' went false — arming {}min grace before negative actions",
                                automationName, node.getNodeId(), node.getCondition().getDurationMinutes());
                        result = true;
                        condResults.put(node.getNodeId(), true);
                    }
                    // else: wasn't active anyway — nothing to hold, let it fail normally
                } else if (nowMs - armedAt < durationMs) {
                    // Still inside the grace window — keep holding true.
                    result = true;
                    condResults.put(node.getNodeId(), true);
                } else {
                    // Grace expired — release it, let result=false flow through to negatives.
                    log.info("⏰ [{}] Node '{}' grace window expired — firing negative actions",
                            automationName, node.getNodeId());
                    stateStore.clearGrace(automationId, node.getNodeId());
                }
            }
        }
        if (!result) {
            List<ExecutionPlan.CompiledAction> negActions = new ArrayList<>();
            if (wasActive && node.getNegativeActions() != null)
                negActions.addAll(node.getNegativeActions());
            return TreeWalkResult.failed(node.getNodeId(), negActions, condResults);
        }

        // ── Leaf node ─────────────────────────────────────────────────────
        if (node.getPositiveChildNodeIds() == null || node.getPositiveChildNodeIds().isEmpty()) {
            List<ExecutionPlan.CompiledAction> posActions =
                    node.getPositiveActions() != null ? node.getPositiveActions() : List.of();
            return TreeWalkResult.passed(posActions, condResults);
        }

        log.debug("{} Node {} children = {}",
                automationName, node.getNodeId(), node.getPositiveChildNodeIds());

        if (!node.isFanout()) {
            // ── AND path: all children must pass ──────────────────────────
            List<ExecutionPlan.CompiledAction> allChildNegActions = new ArrayList<>();
            Map<String, Boolean> allChildCondResults = new LinkedHashMap<>();

            for (String childId : node.getPositiveChildNodeIds()) {
                ExecutionPlan.CompiledConditionNode child = nodeMap.get(childId);
                if (child == null) {
                    log.warn("⚠️ [{}] Child node '{}' referenced by '{}' not found in nodeMap",
                            automationName, childId, node.getNodeId());
                    continue;
                }
                TreeWalkResult childResult = walkNode(child, nodeMap, payload, state,
                        automationId, now, automationName, memoryUpdates, visited,
                        intervalNodesToArm);
                allChildCondResults.putAll(childResult.conditionResults);

                if (!childResult.passed) {
                    allChildNegActions.addAll(childResult.negativeActionsToFire);
                    condResults.putAll(allChildCondResults);
                    return TreeWalkResult.failed(childResult.failedNodeId,
                            allChildNegActions, condResults);
                }
            }

            condResults.putAll(allChildCondResults);
            List<ExecutionPlan.CompiledAction> allPos = node.getPositiveChildNodeIds().stream()
                    .map(nodeMap::get)
                    .filter(Objects::nonNull)
                    .flatMap(n -> n.getPositiveActions() != null
                            ? n.getPositiveActions().stream() : Stream.empty())
                    .toList();
            return TreeWalkResult.passed(allPos, condResults);

        } else {
            // ── OR fan-out path ────────────────────────────────────────────
            List<ExecutionPlan.CompiledAction> allPositiveActions = new ArrayList<>();
            List<ExecutionPlan.CompiledAction> allNegativeActions = new ArrayList<>();
            Map<String, Boolean> allChildCondResults = new LinkedHashMap<>();
            boolean anyPassed = false;
            boolean firstMatch = node.isFirstMatch();

            for (String childId : node.getPositiveChildNodeIds()) {
                ExecutionPlan.CompiledConditionNode child = nodeMap.get(childId);
                if (child == null) {
                    log.warn("⚠️ [{}] Fan-out child '{}' not found in nodeMap", automationName, childId);
                    continue;
                }
                TreeWalkResult childResult = walkNode(child, nodeMap, payload, state,
                        automationId, now, automationName, memoryUpdates, visited,
                        intervalNodesToArm);
                allChildCondResults.putAll(childResult.conditionResults);

                log.debug("{} Child {} result={}", automationName, childId, childResult.passed());

                if (childResult.passed) {
                    allPositiveActions.addAll(childResult.positiveActionsToFire);
                    anyPassed = true;
                    if (firstMatch) break;
                } else {
                    allNegativeActions.addAll(childResult.negativeActionsToFire);
                }
            }

            condResults.putAll(allChildCondResults);

            if (!anyPassed) {
                return TreeWalkResult.failed("fanout@" + node.getNodeId(),
                        allNegativeActions, condResults);
            }

            // For OR fanout, combine negative actions from failing branches FIRST
            // (so devices on failing branches get their off-commands), then the
            // positive actions from passing branches.
            List<ExecutionPlan.CompiledAction> combined = new ArrayList<>(allNegativeActions);
            combined.addAll(allPositiveActions);

            log.debug("{} OR node {} cond results={}", automationName, node.getNodeId(), condResults);
            return TreeWalkResult.passed(combined, condResults);
        }
    }

    private boolean hasNegativeGraceDuration(ExecutionPlan.CompiledCondition c) {
        return c != null
                && c.getDurationMinutes() > 0
                && !isIntervalWithDuration(c);   // interval+duration already has its own hold semantics
    }

    private boolean isIntervalWithDuration(ExecutionPlan.CompiledCondition c) {
        return c != null
                && "scheduled".equals(c.getConditionType())
                && "interval".equals(c.getScheduleType())
                && c.getDurationMinutes() > 0;
    }


    // ─────────────────────────────────────────────────────────────────────
    // ACTIVATE  (top-level — checks isTopLevelActive())
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Used when there are NO branch-level positive actions — i.e. the automation
     * has only top-level actions and we need to check whether the whole automation
     * is already ACTIVE before dispatching.
     * <p>
     * Do NOT call this for OR-fanout paths that carry their own per-branch
     * positiveActions — use the BRANCH_TRIGGERED path in walkConditionTree() instead.
     */
    private EvalResult handleActivate(ExecutionPlan plan,
                                      AutomationRuntimeState state,
                                      EvalResult.EvalResultBuilder result,
                                      ZonedDateTime now) {
        boolean isActive = state.isTopLevelActive();

        if (!isActive) {
            List<ExecutionPlan.CompiledAction> actions = result.build().getActionsToFire();
            if (actions == null || actions.isEmpty())
                actions = plan.getTopLevelPositiveActions() != null
                        ? plan.getTopLevelPositiveActions() : List.of();

            return result
                    .outcome(EvalOutcome.TRIGGERED)
                    .actionsToFire(actions)
                    .nextTopLevelState("ACTIVE")
                    .triggeredAt(Date.from(now.toInstant()))
                    .build();
        } else {
            return result
                    .outcome(EvalOutcome.SKIPPED)
                    .reason("Already active — condition still true")
                    .build();
        }
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

        // ── Secondary device resolution with per-evaluation cache ─────────
        Map<String, Object> payload = primaryPayload;
        if (c.getDeviceId() != null && !c.getDeviceId().isBlank()) {
            payload = resolveSecondaryPayload(c, automationId, now);
            if (payload == null) return false;  // timed out or stale — skip condition
        }

        if ("stale".equals(c.getConditionType())) {
            return evalStale(c, payload, automationId, now);
        }

        String key = c.getTriggerKey();
        if (key == null || key.isBlank()) {
            log.warn("⚠️ [{}] Condition '{}' has no triggerKey", automationId, c.getNodeId());
            return false;
        }
        if (!payload.containsKey(key)) {
            log.warn("⚠️ [{}] Condition '{}': key '{}' missing from payload (available: {})",
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
                double bufLow = Math.max(1.0, Math.abs(a) * 0.02);
                double bufHigh = Math.max(1.0, Math.abs(b) * 0.02);
                yield wasActive ? v > (a - bufLow) && v < (b + bufHigh) : v > a && v < b;
            }
            default -> false;
        };
    }

    /**
     * Resolves the payload for a secondary device condition.
     * Resolution order: per-evaluation cache → Redis → MongoDB DB fallback
     * (only for stale conditions, where missing data IS the signal).
     * Returns null if the data cannot be resolved or is too stale.
     */
    private Map<String, Object> resolveSecondaryPayload(ExecutionPlan.CompiledCondition c,
                                                        String automationId,
                                                        ZonedDateTime now) {
        String deviceId = c.getDeviceId();
        Map<String, Map<String, Object>> cache = SECONDARY_CACHE.get();

        if (cache.containsKey(deviceId)) {
            return cache.get(deviceId);
        }

        Map<String, Object> secondary = redisService.getRecentDeviceData(deviceId);
        if (secondary != null && !secondary.isEmpty()) {
            cache.put(deviceId, secondary);
            return secondary;
        }

        if (!"stale".equals(c.getConditionType())) {
            log.warn("⚠️ [{}] Secondary device '{}' has no Redis data — condition '{}' skipped (not stale type)",
                    automationId, deviceId, c.getNodeId());
            cache.put(deviceId, Map.of());
            return null;
        }

        log.warn("⚠️ [{}] Secondary device '{}' has no Redis data — fetching from DB for stale check",
                automationId, deviceId);
        long dbStart = System.currentTimeMillis();

        try {
            var data = mainService.getLastFullData(deviceId);
            long dbMs = System.currentTimeMillis() - dbStart;
            if (dbMs > 200)
                log.warn("⚠️ [{}] DB fallback for '{}' took {}ms", automationId, deviceId, dbMs);

            Map<String, Object> result = new HashMap<>();
            if (data.getData() != null) result.putAll(data.getData());
            if (data.getUpdateDate() != null)
                result.put("last_seen", data.getUpdateDate().getEpochSecond() * 1000L);

            cache.put(deviceId, result);
            return result;

        } catch (Exception e) {
            log.error("❌ [{}] DB fallback failed for device '{}': {}",
                    automationId, deviceId, e.getMessage());
            cache.put(deviceId, Map.of());
            return null;
        }
    }


    private boolean evalStale(ExecutionPlan.CompiledCondition c,
                              Map<String, Object> payload,
                              String automationId,
                              ZonedDateTime now) {
        String key = c.getTriggerKey() != null && !c.getTriggerKey().isBlank()
                ? c.getTriggerKey() : "last_seen";
        long lastSeenMs = extractLastSeenMs(payload, key);
        if (lastSeenMs <= 0) {
            log.warn("⚠️ [{}] Stale '{}': last_seen not resolvable — treating as STALE",
                    automationId, c.getNodeId());
            return true;
        }
        long thresholdMs = (long) (Double.parseDouble(c.getValue()) * 60_000);
        long staleMs = now.toInstant().toEpochMilli() - lastSeenMs;
        boolean isStale = staleMs > thresholdMs;
        log.debug("⏱️ [{}] Stale '{}': last_seen={}s ago, threshold={}min → {}",
                automationId, c.getNodeId(), staleMs / 1000, c.getValue(),
                isStale ? "STALE" : "FRESH");
        return isStale;
    }

    private long extractLastSeenMs(Map<String, Object> payload, String key) {
        if (payload == null || !payload.containsKey(key)) return 0L;
        Object raw = payload.get(key);
        switch (raw) {
            case null -> {
                return 0L;
            }
            case Number n -> {
                long v = n.longValue();
                return v < 10_000_000_000L ? v * 1000L : v;
            }
            case Date d -> {
                return d.getTime();
            }
            default -> {
            }
        }
        String s = raw.toString().trim();
        try {
            long v = Long.parseLong(s);
            return v < 10_000_000_000L ? v * 1000L : v;
        } catch (NumberFormatException ignored) {
        }
        try {
            return java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli();
        } catch (Exception ignored) {
        }
        try {
            return java.time.Instant.parse(s).toEpochMilli();
        } catch (Exception ignored) {
        }
        log.warn("⚠️ Unparseable last_seen '{}' (type={})", s, raw.getClass().getSimpleName());
        return 0L;
    }


    // ─────────────────────────────────────────────────────────────────────
    // SCHEDULE EVALUATION
    // ─────────────────────────────────────────────────────────────────────

    boolean evalScheduled(ExecutionPlan.CompiledCondition c,
                          String automationId, ZonedDateTime now) {
        LocalTime current = now.toLocalTime();

        if (c.getDays() != null && !c.getDays().isEmpty()) {
            String dow = now.getDayOfWeek()
                    .getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH);
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
            if (stateStore.intervalKeyExists(automationId, c.getNodeId())) return false;
            log.debug("🕒 [{}] Interval '{}' ready to fire", automationId, c.getNodeId());
            return true;
        }

        LocalTime target = parseTime(c.getTime());
        if (target == null) return false;
        if (Math.abs(ChronoUnit.MINUTES.between(target, current)) > 1) return false;
        return !stateStore.dailyFireKeyExists(automationId, now.toLocalDate().toString());
    }


    // ─────────────────────────────────────────────────────────────────────
    // MEMORY POLICY
    // ─────────────────────────────────────────────────────────────────────

    private record MemoryPolicyResult(boolean passes, ConditionMemory updatedMemory,
                                      String memorySummary) {
    }

    private MemoryPolicyResult applyMemoryPolicy(ConditionMemoryPolicy policy,
                                                 boolean rawResult,
                                                 ConditionMemory memory,
                                                 ZonedDateTime now) {
        long nowMs = now.toInstant().toEpochMilli();

        return switch (policy.getType()) {
            case DURATION -> {
                if (!rawResult) {
                    yield new MemoryPolicyResult(false, memory.withRawFalse(), "DURATION: reset (false)");
                }
                long firstTrue = memory.getFirstTrueEpochMs() > 0
                        ? memory.getFirstTrueEpochMs() : nowMs;
                ConditionMemory updated = memory.withRawTrue(firstTrue).withPolicyPassed(false);
                long elapsedSec = (nowMs - firstTrue) / 1000;
                boolean passes = elapsedSec >= policy.getRequiredDurationSeconds();
                updated = updated.withPolicyPassed(passes);
                yield new MemoryPolicyResult(passes, updated,
                        "DURATION: " + elapsedSec + "/" + policy.getRequiredDurationSeconds() + "s");
            }
            case CONSECUTIVE_TICKS -> {
                if (!rawResult) {
                    yield new MemoryPolicyResult(false, memory.withRawFalse(), "CONSECUTIVE: reset (false)");
                }
                ConditionMemory updated = memory.withRawTrue(nowMs);
                int count = updated.getConsecutiveTrueCount();
                boolean passes = count >= policy.getRequiredTicks();
                updated = updated.withPolicyPassed(passes);
                yield new MemoryPolicyResult(passes, updated,
                        "CONSECUTIVE: " + count + "/" + policy.getRequiredTicks());
            }
            case EDGE_RISING -> {
                Boolean prev = memory.getPreviousRawResult();
                boolean edge = rawResult && (prev == null || !prev);
                ConditionMemory updated = (rawResult ? memory.withRawTrue(nowMs) : memory.withRawFalse())
                        .withPolicyPassed(edge);
                yield new MemoryPolicyResult(edge, updated,
                        edge ? "EDGE_RISING: fired" : "EDGE_RISING: no edge (raw=" + rawResult + ")");
            }
            case EDGE_FALLING -> {
                Boolean prev = memory.getPreviousRawResult();
                boolean edge = !rawResult && (prev != null && prev);
                ConditionMemory updated = (rawResult ? memory.withRawTrue(nowMs) : memory.withRawFalse())
                        .withPolicyPassed(edge);
                yield new MemoryPolicyResult(edge, updated,
                        edge ? "EDGE_FALLING: fired" : "EDGE_FALLING: no edge (raw=" + rawResult + ")");
            }
            case EDGE_BOTH -> {
                Boolean prev = memory.getPreviousRawResult();
                boolean edge = prev == null ? rawResult : (rawResult != prev);
                ConditionMemory updated = (rawResult ? memory.withRawTrue(nowMs) : memory.withRawFalse())
                        .withPolicyPassed(edge);
                yield new MemoryPolicyResult(edge, updated,
                        edge ? "EDGE_BOTH: fired (" + prev + "→" + rawResult + ")" : "EDGE_BOTH: no edge");
            }
        };
    }

    public String summarizeMemory(ConditionMemoryPolicy policy, ConditionMemory memory) {
        if (policy == null || memory == null) return null;
        return switch (policy.getType()) {
            case DURATION -> "DURATION: "
                    + (memory.getFirstTrueEpochMs() > 0
                    ? (System.currentTimeMillis() - memory.getFirstTrueEpochMs()) / 1000 : 0)
                    + "/" + policy.getRequiredDurationSeconds() + "s";
            case CONSECUTIVE_TICKS ->
                    "CONSECUTIVE: " + memory.getConsecutiveTrueCount() + "/" + policy.getRequiredTicks();
            case EDGE_RISING -> "EDGE_RISING: prev=" + memory.getPreviousRawResult();
            case EDGE_FALLING -> "EDGE_FALLING: prev=" + memory.getPreviousRawResult();
            case EDGE_BOTH -> "EDGE_BOTH: prev=" + memory.getPreviousRawResult();
        };
    }


    // ─────────────────────────────────────────────────────────────────────
    // TREE WALK RESULT
    // ─────────────────────────────────────────────────────────────────────

    private record TreeWalkResult(boolean passed,
                                  String failedNodeId,
                                  List<ExecutionPlan.CompiledAction> negativeActionsToFire,
                                  List<ExecutionPlan.CompiledAction> positiveActionsToFire,
                                  Map<String, Boolean> conditionResults) {

        private TreeWalkResult(boolean passed, String failedNodeId,
                               List<ExecutionPlan.CompiledAction> negativeActionsToFire,
                               List<ExecutionPlan.CompiledAction> positiveActionsToFire,
                               Map<String, Boolean> conditionResults) {
            this.passed = passed;
            this.failedNodeId = failedNodeId;
            this.negativeActionsToFire = negativeActionsToFire != null
                    ? negativeActionsToFire : List.of();
            this.positiveActionsToFire = positiveActionsToFire != null
                    ? positiveActionsToFire : List.of();
            this.conditionResults = conditionResults != null ? conditionResults : Map.of();
        }

        static TreeWalkResult failed(String nodeId,
                                     List<ExecutionPlan.CompiledAction> negActions,
                                     Map<String, Boolean> condResults) {
            return new TreeWalkResult(false, nodeId, negActions, List.of(), condResults);
        }

        static TreeWalkResult passed(List<ExecutionPlan.CompiledAction> posActions,
                                     Map<String, Boolean> condResults) {
            return new TreeWalkResult(true, null, List.of(), posActions, condResults);
        }
    }


    // ─────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────

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
            LocalDate today = LocalDate.now(IST);
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
            ZonedDateTime nowZ = ZonedDateTime.now(IST);
            long ttl = ChronoUnit.SECONDS.between(nowZ, nowZ.plusDays(1).truncatedTo(ChronoUnit.DAYS));
            redisService.setWithExpiry(cacheKey, result.toString(), ttl);
            return result;
        } catch (Exception e) {
            log.error("❌ Sun time fetch failed: {}", e.getMessage());
            return null;
        }
    }


    // ─────────────────────────────────────────────────────────────────────
    // RESULT / OUTCOME TYPES
    // ─────────────────────────────────────────────────────────────────────

    public enum EvalOutcome {
        TRIGGERED,
        /**
         * An OR fanout branch transitioned inactive→active and dispatched its own
         * per-branch positive actions. The top-level automation state is ACTIVE
         * (or becomes ACTIVE), but the trigger came from a specific branch node
         * rather than the automation as a whole.
         * <p>
         * Distinct from TRIGGERED so the orchestrator knows NOT to reset
         * topLevelState on every BRANCH_TRIGGERED — topLevelState is already
         * ACTIVE and should remain so until ALL branches fail (C1_NEGATIVE).
         */
        BRANCH_TRIGGERED,
        C1_NEGATIVE,
        SKIPPED,
        NOT_MET,
        STATELESS_FIRE,
        FALLBACK
    }

    @lombok.Builder(toBuilder = true)
    @lombok.Value
    public static class EvalResult {
        String automationId;
        Date evaluatedAt;
        boolean c1True;
        EvalOutcome outcome;
        String reason;
        Map<String, Boolean> conditionResults;
        List<ExecutionPlan.CompiledAction> actionsToFire;
        String nextTopLevelState;
        Date triggeredAt;
        boolean anyWasActive;
        String traceId;
        Long evalDurationMs;
        boolean shouldArmIntervalCooldown;
        String intervalCooldownNodeId;
        long intervalCooldownTtlSeconds;
        boolean shouldWriteDailySolarKey;
        boolean shouldWriteDailyFireKey;
        Map<String, ConditionMemory> memoryUpdates;

        /**
         * BUG 2 fix: nodeIds of interval-scheduled conditions (durationMinutes>0)
         * that evaluated true THIS tick. The orchestrator arms
         * stateStore.setRunningKey() for each of these after a successful
         * positive-action dispatch.
         */
        Set<String> intervalNodesToArm;

        public boolean hasActions() {
            return actionsToFire != null && !actionsToFire.isEmpty();
        }

        public boolean hasChanges() {
            return outcome == EvalOutcome.TRIGGERED
                    || outcome == EvalOutcome.BRANCH_TRIGGERED  // ← BUG 4 fix
                    || outcome == EvalOutcome.C1_NEGATIVE
                    || outcome == EvalOutcome.STATELESS_FIRE
                    || outcome == EvalOutcome.FALLBACK;
        }
    }
}