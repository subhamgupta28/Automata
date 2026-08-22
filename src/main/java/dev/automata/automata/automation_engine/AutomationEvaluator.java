package dev.automata.automata.automation_engine;

import dev.automata.automata.automation_engine.condition_operator_strategy.ConditionOperatorRegistry;
import dev.automata.automata.automation_engine.condition_schedule_strategy.ScheduleRegistry;
import dev.automata.automata.automation_engine.device_data.ChainedPayloadResolver;
import dev.automata.automata.automation_engine.device_data.InMemoryCache;
import dev.automata.automata.automation_engine.dto.MemoryPolicyResult;
import dev.automata.automata.automation_engine.dto.TreeWalkResult;
import dev.automata.automata.automation_engine.enums.EvalOutcome;
import dev.automata.automata.automation_engine.grace.GraceWindowEvaluator;
import dev.automata.automata.automation_engine.memory_policy_strategy.MemoryPolicyRegistry;
import dev.automata.automata.dto.AutomationRuntimeState;
import dev.automata.automata.dto.ConditionMemory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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

    private final AutomationStateStore stateStore;
    private final ConditionOperatorRegistry operatorRegistry;
    private final ScheduleRegistry scheduleRegistry;
    private final MemoryPolicyRegistry memoryPolicyStrategy;
    private final ChainedPayloadResolver chainedPayloadResolver;
    private final GraceWindowEvaluator graceWindowEvaluator;

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");


    private final InMemoryCache inMemoryCache;


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

        inMemoryCache.clear();

        try {
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

            return built;
        } finally {
            // Runs even if walkConditionTree/handleActivate throws — prevents stale
            // secondary-device data from leaking into the next evaluation reusing
            // this pooled thread.
            inMemoryCache.clear();
        }
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

            Map<String, Boolean> allConditionResults = new LinkedHashMap<>(walkResult.conditionResults());

            if (!walkResult.passed()) {
                log.debug("🌿 [{}] Tree walk failed at '{}' — {} negative action(s)",
                        plan.getAutomationName(), walkResult.failedNodeId(),
                        walkResult.negativeActionsToFire().size());

                boolean anyNodeWasActive = plan.getConditionTree().stream()
                        .anyMatch(n -> n.isStateful() && state.isNodeActive(n.getNodeId()));

                List<ExecutionPlan.CompiledAction> toFire = new ArrayList<>();
                if (anyNodeWasActive && plan.getInformationalActions() != null)
                    toFire.addAll(plan.getInformationalActions());
                toFire.addAll(walkResult.negativeActionsToFire());

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

            List<ExecutionPlan.CompiledAction> branchActions = walkResult.positiveActionsToFire();

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
                            .actionsToFire(walkResult.positiveActionsToFire()),
                    now);
            return activated.toBuilder().intervalNodesToArm(intervalNodesToArm).build();
        }

        // No root nodes evaluated
        return handleActivate(plan, state, result.c1True(true), now)
                .toBuilder().intervalNodesToArm(Set.of()).build();
    }

    /**
     * Shared iteration for all three child-walk loops in walkNode() (AND path,
     * OR fan-out, negative-branch children). Walks each childId in order,
     * recurses via walkNode(), merges conditionResults, and lets `accumulator`
     * decide what to collect and whether to stop early.
     */
    private Map<String, Boolean> walkChildren(List<String> childIds,
                                              Map<String, ExecutionPlan.CompiledConditionNode> nodeMap,
                                              Map<String, Object> payload,
                                              AutomationRuntimeState state,
                                              String automationId,
                                              ZonedDateTime now,
                                              String automationName,
                                              Map<String, ConditionMemory> memoryUpdates,
                                              Set<String> visited,
                                              Set<String> intervalNodesToArm,
                                              ChildAccumulator accumulator) {
        Map<String, Boolean> merged = new LinkedHashMap<>();
        for (String childId : childIds) {
            ExecutionPlan.CompiledConditionNode child = nodeMap.get(childId);
            if (child == null) {
                log.warn("⚠️ [{}] Child node '{}' not found in nodeMap", automationName, childId);
                continue;
            }
            TreeWalkResult childResult = walkNode(child, nodeMap, payload, state,
                    automationId, now, automationName, memoryUpdates, visited, intervalNodesToArm);
            merged.putAll(childResult.conditionResults());
            if (accumulator.accept(childResult)) break; // short-circuit: AND-fail-fast / FIRST_MATCH
        }
        return merged;
    }

    @FunctionalInterface
    private interface ChildAccumulator {
        /**
         * Called once per child result, in declared order. Return true to stop iterating.
         */
        boolean accept(TreeWalkResult childResult);
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
            result = policyResult.passes();
            memoryUpdates.put(node.getNodeId(), policyResult.updatedMemory());
            log.debug("  🧠 [{}] Node '{}' raw={} memory={} → passes={}",
                    automationName, node.getNodeId(), rawResult,
                    policyResult.memorySummary(), result);
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
        // FIX: a memory-policy "no pass" (e.g. EDGE_RISING with no new edge) is NOT
        // the same as the underlying condition going false. rawResult still reflects
        // reality; only rawResult==false should be eligible to trigger negativeActions.
        // Without this, an EDGE_RISING leaf silently re-fires its own negative actions
        // one tick after it fires, because wasActive=true + result=false (steady-state,
        // no edge) falls through into the failed-node negative-action path below.
        if (!result && rawResult && node.hasMemoryPolicy() && wasActive) {
            log.debug("  ⏸️ [{}] Node '{}' steady-state true (no edge/policy re-trigger) — "
                            + "no-op, skipping negative actions",
                    automationName, node.getNodeId());
            return TreeWalkResult.passed(List.of(), condResults);
        }
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
            log.debug("⏳ [{}] Node '{}' grace check — result={} wasActive={} durationRaw={}",
                    automationName, node.getNodeId(), result, wasActive,
                    node.getCondition().getDurationMinutes());
            long nowMs = now.toInstant().toEpochMilli();
            long durationMs = node.getCondition().getDurationMinutes() * 1000L;

            if (result) {
                stateStore.clearGrace(automationId, node.getNodeId());
            } else {
                GraceWindowEvaluator.GraceDecision decision =
                        graceWindowEvaluator.evaluate(automationId, node.getNodeId(), durationMs, wasActive, nowMs);
                if (decision.hold()) {
                    result = true;
                    condResults.put(node.getNodeId(), true);
                }
            }
        }
        if (!result) {
            List<ExecutionPlan.CompiledAction> negActions = new ArrayList<>();
            if (wasActive && node.getNegativeActions() != null)
                negActions.addAll(node.getNegativeActions());

            // ── Walk negative-branch children ──────────────────────────────────
            // A node can have children gated on IT being false (cond-negative handle).
            // These were previously compiled but never traversed. Walk them now: if
            // ALL of them independently evaluate true, treat this subtree as PASSED,
            // combining this node's own negativeActions (e.g. "dim down") with the
            // negative-child's positiveActions (e.g. "turn off entirely"). If any
            // negative child itself fails, fall through to the normal failed() path,
            // accumulating that child's negatives too.
            if (node.getNegativeChildNodeIds() != null && !node.getNegativeChildNodeIds().isEmpty()) {
                List<ExecutionPlan.CompiledAction> negChildPosActions = new ArrayList<>();
                AtomicBoolean allNegChildrenPassed = new AtomicBoolean(true);

                Map<String, Boolean> negChildCondResults = walkChildren(
                        node.getNegativeChildNodeIds(), nodeMap, payload, state, automationId, now,
                        automationName, memoryUpdates, visited, intervalNodesToArm,
                        childResult -> {
                            if (childResult.passed()) {
                                negChildPosActions.addAll(childResult.positiveActionsToFire());
                            } else {
                                allNegChildrenPassed.set(false);
                                negActions.addAll(childResult.negativeActionsToFire());
                            }
                            return false; // walk every negative-branch child — never short-circuits
                        });

                condResults.putAll(negChildCondResults);

                if (allNegChildrenPassed.get() && !negChildPosActions.isEmpty()) {
                    log.debug("🌙 [{}] Node '{}' false — negative-branch child chain passed, "
                                    + "combining {} own negative + {} child action(s)",
                            automationName, node.getNodeId(), negActions.size(), negChildPosActions.size());
                    List<ExecutionPlan.CompiledAction> combined = new ArrayList<>(negActions);
                    combined.addAll(negChildPosActions);
                    return TreeWalkResult.passed(combined, condResults);
                }
                // else: at least one negative-branch child failed — fall through below
                // with accumulated negatives from this node and its failed child(ren).
            }

            return TreeWalkResult.failed(node.getNodeId(), negActions, condResults);
        }

        // ── Leaf node ─────────────────────────────────────────────────────
        if (node.getPositiveChildNodeIds() == null || node.getPositiveChildNodeIds().isEmpty()) {
            List<ExecutionPlan.CompiledAction> posActions =
                    node.getPositiveActions() != null ? node.getPositiveActions() : List.of();

            // EVERY_TICK throttle: if this leaf has no memory policy (meaning it's
            // running in EVERY_TICK mode, not ON_STATE_CHANGE/EDGE_RISING) and has
            // a resend throttle configured, suppress actions if we fired recently.
            if (!node.hasMemoryPolicy() && node.getMinResendIntervalSeconds() > 0) {
                if (stateStore.throttleKeyExists(automationId, node.getNodeId())) {
                    log.debug("🕑 [{}] Node '{}' throttled — suppressing repeat actions this tick",
                            automationName, node.getNodeId());
                    return TreeWalkResult.passed(List.of(), condResults); // passed=true but no actions
                }
            }

            return TreeWalkResult.passed(posActions, condResults);
        }

        log.debug("{} Node {} children = {}",
                automationName, node.getNodeId(), node.getPositiveChildNodeIds());

        if (!node.isFanout()) {
            // ── AND path: all children must pass ──────────────────────────
            List<ExecutionPlan.CompiledAction> allChildPosActions = new ArrayList<>();
            AtomicReference<TreeWalkResult> failure = new AtomicReference<>();

            Map<String, Boolean> allChildCondResults = walkChildren(
                    node.getPositiveChildNodeIds(), nodeMap, payload, state, automationId, now,
                    automationName, memoryUpdates, visited, intervalNodesToArm,
                    childResult -> {
                        if (!childResult.passed()) {
                            failure.set(childResult);
                            return true; // AND fails fast — stop walking remaining siblings
                        }
                        allChildPosActions.addAll(childResult.positiveActionsToFire());
                        return false;
                    });

            condResults.putAll(allChildCondResults);

            if (failure.get() != null) {
                return TreeWalkResult.failed(failure.get().failedNodeId(),
                        failure.get().negativeActionsToFire(), condResults);
            }
            return TreeWalkResult.passed(allChildPosActions, condResults);
        } else {
            // ── OR fan-out path ────────────────────────────────────────────
            List<ExecutionPlan.CompiledAction> allPositiveActions = new ArrayList<>();
            List<ExecutionPlan.CompiledAction> allNegativeActions = new ArrayList<>();
            AtomicBoolean anyPassed = new AtomicBoolean(false);
            boolean firstMatch = node.isFirstMatch();
            log.debug("🔀 [{}] Fanout node '{}' firstMatch={} children={}",
                    automationName, node.getNodeId(), firstMatch, node.getPositiveChildNodeIds());

            Map<String, Boolean> allChildCondResults = walkChildren(
                    node.getPositiveChildNodeIds(), nodeMap, payload, state, automationId, now,
                    automationName, memoryUpdates, visited, intervalNodesToArm,
                    childResult -> {
                        log.debug("🔀 [{}] Child passed={} — breaking={}",
                                automationName, childResult.passed(), firstMatch && childResult.passed());
                        if (childResult.passed()) {
                            allPositiveActions.addAll(childResult.positiveActionsToFire());
                            anyPassed.set(true);
                            return firstMatch; // FIRST_MATCH stops at the first passing branch
                        } else {
                            allNegativeActions.addAll(childResult.negativeActionsToFire());
                            return false;
                        }
                    });

            condResults.putAll(allChildCondResults);

            if (!anyPassed.get()) {
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
                && !isIntervalWithDuration(c)
                && !"scheduled".equals(c.getConditionType());  // ← range/above/below/equal/stale all pass this
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
            if (payload == null) {
                // FIX: no fresh secondary data this tick is NOT the same as the
                // condition being false. Treat it as inconclusive and hold the
                // node's previous active state, so a slow-reporting secondary
                // device doesn't spuriously reset DURATION memory or trip
                // C1_NEGATIVE on ticks driven purely by the (faster) primary
                // trigger device.
                log.debug("⏸️ [{}] Secondary device data unavailable for '{}' — holding previous state ({})",
                        automationId, c.getNodeId(), wasActive);
                return wasActive;
            }
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
        if (!raw.matches("-?\\d+(\\.\\d+)?")) {
            boolean expectsNumeric = List.of("above", "below", "equal")
                    .contains(c.getConditionType());
            if (expectsNumeric) {
                log.warn("⚠️ [{}] Condition '{}' expects numeric comparison but got non-numeric "
                                + "payload value '{}' for key '{}' — falling back to string equality",
                        automationId, c.getNodeId(), raw, key);
            }
            return raw.equals(c.getValue());
        }

        double v = Double.parseDouble(raw);
        if (c.isExact()) return c.getValue().equals(raw);
        return operatorRegistry.find(c.getConditionType())
                .map(op -> op.evaluate(c, v, raw, wasActive))
                .orElse(false);
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
        return chainedPayloadResolver.resolve(deviceId, "stale".equals(c.getConditionType()), automationId);
    }


    private boolean evalStale(
            ExecutionPlan.CompiledCondition c,
            Map<String, Object> payload,
            String automationId,
            ZonedDateTime now
    ) {
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
    boolean evalScheduled(ExecutionPlan.CompiledCondition c, String automationId, ZonedDateTime now) {
        if (c.getDays() != null && !c.getDays().isEmpty()) {
            String dow = now.getDayOfWeek()
                    .getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH);
            dow = dow.substring(0, 1).toUpperCase() + dow.substring(1).toLowerCase();
            if (!c.getDays().contains("Everyday") && !c.getDays().contains(dow)) return false;
        }
        String st = c.getScheduleType() != null ? c.getScheduleType() : "at";
        return scheduleRegistry.find(st)
                .map(ev -> ev.evaluate(c, automationId, now))
                .orElse(false);
    }


    // ─────────────────────────────────────────────────────────────────────
    // MEMORY POLICY
    // ─────────────────────────────────────────────────────────────────────


    private MemoryPolicyResult applyMemoryPolicy(
            ConditionMemoryPolicy policy,
            boolean rawResult,
            ConditionMemory memory,
            ZonedDateTime now
    ) {
        long nowMs = now.toInstant().toEpochMilli();
        return memoryPolicyStrategy.find(policy.getType()).map(m -> m.apply(policy, rawResult, memory, nowMs)).orElse(null);
    }

    public String summarizeMemory(ConditionMemoryPolicy policy, ConditionMemory memory) {
        if (policy == null || memory == null) return null;
        return memoryPolicyStrategy.find(policy.getType()).map(m -> m.summarize(policy, memory)).orElse(null);
    }


}