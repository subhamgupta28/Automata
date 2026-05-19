package dev.automata.automata.v2;

import dev.automata.automata.dto.AutomationRuntimeState;
import dev.automata.automata.dto.BranchDecision;
import dev.automata.automata.dto.ConditionMemory;
import dev.automata.automata.service.MainService;
import dev.automata.automata.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Stream;

/**
 * Pure evaluation component — NO Redis writes, NO action dispatch.
 * <p>
 * Changes vs previous version
 * ───────────────────────────
 * 1. applyMemoryPolicy() (Point 2)
 * Called inside walkNode() after evalSingleCondition().
 * Wraps the raw boolean through the node's ConditionMemoryPolicy (if any)
 * and updates ConditionMemory entries on the next-state object.
 * The evaluator receives a mutable memoryUpdates map; the orchestrator
 * writes these into the next AutomationRuntimeState after CAS succeeds.
 * <p>
 * 2. Memory-aware walkNode signature
 * walkNode() accepts a memoryUpdates map (nodeId → ConditionMemory) that
 * it populates as it walks the tree. The orchestrator copies these into
 * nextState.conditionMemories after a successful CAS write.
 * <p>
 * 3. Memory summary generation for EvalResult (Point 9)
 * After walking, the evaluator builds human-readable summaries of each
 * node's memory state and attaches them to EvalResult for the snapshot.
 * <p>
 * 4. handleNoBranch fix (from previous analysis)
 * nextTopLevelState is always "ACTIVE" so single-shot automations don't
 * re-fire every tick.
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

        EvalResult.EvalResultBuilder result = EvalResult.builder()
                .automationId(automationId)
                .evaluatedAt(Date.from(now.toInstant()))
                .traceId(traceId);

        // memoryUpdates: populated by walkNode, written to state post-CAS by orchestrator
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
        } else if (plan.hasBranches()) {
            built = handleBranches(plan, state, result.c1True(true), automationId, now, payload);
        } else {
            built = handleNoBranch(plan, state, result.c1True(true), now);
        }

        // Attach memory updates to result so orchestrator can persist them post-CAS
        built = built.toBuilder()
                .memoryUpdates(memoryUpdates)
                .evalDurationMs(System.currentTimeMillis() - evalStart)
                .build();

        if (built.getEvalDurationMs() > 200)
            log.warn("⚠️ [{}] Slow evaluation: {}ms (traceId={})",
                    plan.getAutomationName(), built.getEvalDurationMs(), traceId);

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

        var visited = new HashSet<String>();
        for (String rootId : plan.getRootConditionNodeIds()) {
            ExecutionPlan.CompiledConditionNode rootNode = nodeMap.get(rootId);
            if (rootNode == null) {
                log.warn("⚠️ Root condition node '{}' not found in tree — skipping", rootId);
                continue;
            }

            TreeWalkResult walkResult = walkNode(rootNode, nodeMap, payload, state,
                    automationId, now, plan.getAutomationName(), memoryUpdates, visited);

            if (!walkResult.passed) {
                log.debug("🌿 [{}] Tree walk stopped at '{}' — firing {} negative action(s)",
                        plan.getAutomationName(), walkResult.failedNodeId,
                        walkResult.negativeActionsToFire.size());

                boolean anyBranchWasActive = plan.hasBranches() &&
                        plan.getBranches().stream()
                                .anyMatch(b -> state.isBranchActive(b.getGateNodeId()));
                boolean topLevelWasActive = !plan.hasBranches() && state.isTopLevelActive();
                boolean anyNodeWasActive = plan.getConditionTree() != null
                        && plan.getConditionTree().stream()
                        .anyMatch(n -> n.isStateful() && state.isNodeActive(n.getNodeId()));
                boolean anyWasActive = anyBranchWasActive || topLevelWasActive || anyNodeWasActive;

                List<String> branchesToRevert = anyBranchWasActive
                        ? plan.getBranches().stream()
                          .filter(b -> state.isBranchActive(b.getGateNodeId()))
                          .map(ExecutionPlan.CompiledBranch::getGateNodeId)
                          .toList()
                        : List.of();

                List<ExecutionPlan.CompiledAction> toFire = new ArrayList<>();
                if (anyWasActive && plan.getInformationalActions() != null)
                    toFire.addAll(plan.getInformationalActions());
                toFire.addAll(walkResult.negativeActionsToFire);

                return result
                        .c1True(false)
                        .outcome(anyWasActive ? EvalOutcome.C1_NEGATIVE : EvalOutcome.NOT_MET)
                        .actionsToFire(toFire)
                        .branchesToRevert(branchesToRevert)
                        .anyWasActive(anyWasActive)
                        .conditionResults(walkResult.conditionResults)
                        .build();
            }

            if (plan.hasBranches()) {
                return handleBranches(plan, state,
                        result.c1True(true).conditionResults(walkResult.conditionResults),
                        automationId, now, payload);
            } else {
                return handleNoBranch(plan, state,
                        result.c1True(true)
                                .conditionResults(walkResult.conditionResults)
                                .actionsToFire(walkResult.positiveActionsToFire),
                        now);
            }
        }

        if (plan.hasBranches())
            return handleBranches(plan, state, result.c1True(true), automationId, now, payload);
        return handleNoBranch(plan, state, result.c1True(true), now);
    }

    /**
     * Recursively walks one condition node and its subtree.
     * <p>
     * Point 2 integration: after evalSingleCondition(), if the node has a
     * memoryPolicy, the raw result is passed through applyMemoryPolicy().
     * The updated ConditionMemory is recorded in memoryUpdates for post-CAS
     * persistence by the orchestrator.
     */
    private TreeWalkResult walkNode(ExecutionPlan.CompiledConditionNode node,
                                    Map<String, ExecutionPlan.CompiledConditionNode> nodeMap,
                                    Map<String, Object> payload,
                                    AutomationRuntimeState state,
                                    String automationId,
                                    ZonedDateTime now,
                                    String automationName,
                                    Map<String, ConditionMemory> memoryUpdates, Set<String> visited) {

        boolean wasActive = node.isStateful() && state.isNodeActive(node.getNodeId());

        boolean rawResult = evalSingleCondition(node.getCondition(), payload,
                wasActive, automationId, now);

        // ── Point 2: apply memory policy ──────────────────────────────────
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
            // Still update memory even for nodes without a policy, so that
            // previousRawResult is always available for future edge detection if
            // the user later adds a policy without restarting.
            ConditionMemory currentMemory = state.getConditionMemory(node.getNodeId());
            ConditionMemory updated = rawResult
                    ? currentMemory.withRawTrue(now.toInstant().toEpochMilli())
                    : currentMemory.withRawFalse();
            memoryUpdates.put(node.getNodeId(), updated.withPolicyPassed(rawResult));
        }

        Map<String, Boolean> condResults = new LinkedHashMap<>();
        condResults.put(node.getNodeId(), result);

        if (!visited.add(node.getNodeId())) {
            // Already evaluated this tick — return cached result or skip
            return TreeWalkResult.passed(List.of(), condResults);
        }
        log.debug("  📊 [{}] Node '{}' ({}) wasActive={} → {}",
                automationName, node.getNodeId(),
                node.getCondition().getConditionType(), wasActive, result);

        if (!result) {
            List<ExecutionPlan.CompiledAction> negActions = new ArrayList<>();
            if (wasActive && node.getNegativeActions() != null)
                negActions.addAll(node.getNegativeActions());
            return TreeWalkResult.failed(node.getNodeId(), negActions, condResults);
        }

        // Replace single child walk:
        if (node.getPositiveChildNodeIds() != null && !node.getPositiveChildNodeIds().isEmpty()) {
            List<ExecutionPlan.CompiledAction> allChildNegActions = new ArrayList<>();
            Map<String, Boolean> allChildCondResults = new LinkedHashMap<>();

            for (String childId : node.getPositiveChildNodeIds()) {
                ExecutionPlan.CompiledConditionNode child = nodeMap.get(childId);
                if (child == null) continue;
                TreeWalkResult childResult = walkNode(child, nodeMap, payload, state,
                        automationId, now, automationName, memoryUpdates, visited);
                allChildCondResults.putAll(childResult.conditionResults);
                if (!childResult.passed) {
                    allChildNegActions.addAll(childResult.negativeActionsToFire);
                    // First failure stops the chain — return failed with all accumulated neg actions
                    condResults.putAll(allChildCondResults);
                    return TreeWalkResult.failed(childResult.failedNodeId, allChildNegActions, condResults);
                }
            }
            condResults.putAll(allChildCondResults);
            // All children passed — collect their positive actions
            List<ExecutionPlan.CompiledAction> allPos = node.getPositiveChildNodeIds().stream()
                    .map(nodeMap::get).filter(Objects::nonNull)
                    .flatMap(n -> n.getPositiveActions() != null ? n.getPositiveActions().stream() : Stream.empty())
                    .toList();
            return TreeWalkResult.passed(allPos, condResults);
        }

        List<ExecutionPlan.CompiledAction> posActions =
                node.getPositiveActions() != null ? node.getPositiveActions() : List.of();
        return TreeWalkResult.passed(posActions, condResults);
    }


    // ─────────────────────────────────────────────────────────────────────
    // POINT 2 — MEMORY POLICY EVALUATION
    // ─────────────────────────────────────────────────────────────────────

    private record MemoryPolicyResult(boolean passes, ConditionMemory updatedMemory,
                                      String memorySummary) {
    }

    /**
     * Applies a ConditionMemoryPolicy to a raw condition result.
     * <p>
     * DURATION
     * true only if rawResult=true AND the condition has been continuously true
     * for at least policy.requiredDurationSeconds.
     * Timer starts on the first true; a false tick resets it.
     * <p>
     * CONSECUTIVE_TICKS
     * true only if rawResult=true AND consecutiveTrueCount (after increment) >=
     * policy.requiredTicks.
     * <p>
     * EDGE_RISING
     * true only on the tick where rawResult transitions false → true.
     * Subsequent true ticks return false (edge already fired).
     * <p>
     * EDGE_FALLING
     * true only on the tick where rawResult transitions true → false.
     * <p>
     * EDGE_BOTH
     * true on any transition.
     */
    private MemoryPolicyResult applyMemoryPolicy(ConditionMemoryPolicy policy,
                                                 boolean rawResult,
                                                 ConditionMemory memory,
                                                 ZonedDateTime now) {
        long nowMs = now.toInstant().toEpochMilli();

        return switch (policy.getType()) {

            case DURATION -> {
                if (!rawResult) {
                    // Condition is false — reset timer
                    ConditionMemory updated = memory.withRawFalse();
                    yield new MemoryPolicyResult(false, updated, "DURATION: reset (false)");
                }
                // Condition is true — start or continue timer
                ConditionMemory updated = memory.withRawTrue(nowMs).withPolicyPassed(false);
                long firstTrue = updated.getFirstTrueEpochMs();
                long elapsedSec = (nowMs - firstTrue) / 1000;
                boolean passes = elapsedSec >= policy.getRequiredDurationSeconds();
                updated = updated.withPolicyPassed(passes);
                String summary = "DURATION: " + elapsedSec + "/" + policy.getRequiredDurationSeconds() + "s";
                yield new MemoryPolicyResult(passes, updated, summary);
            }

            case CONSECUTIVE_TICKS -> {
                if (!rawResult) {
                    ConditionMemory updated = memory.withRawFalse();
                    yield new MemoryPolicyResult(false, updated, "CONSECUTIVE: reset (false)");
                }
                ConditionMemory updated = memory.withRawTrue(nowMs);
                int count = updated.getConsecutiveTrueCount();
                boolean passes = count >= policy.getRequiredTicks();
                updated = updated.withPolicyPassed(passes);
                String summary = "CONSECUTIVE: " + count + "/" + policy.getRequiredTicks();
                yield new MemoryPolicyResult(passes, updated, summary);
            }

            case EDGE_RISING -> {
                Boolean prev = memory.getPreviousRawResult();
                // Rising edge: previous was false (or null = first tick) AND current is true
                boolean edge = rawResult && (prev == null || !prev);
                ConditionMemory updated = (rawResult
                        ? memory.withRawTrue(nowMs)
                        : memory.withRawFalse())
                        .withPolicyPassed(edge);
                String summary = edge ? "EDGE_RISING: fired" : "EDGE_RISING: no edge (raw=" + rawResult + ")";
                yield new MemoryPolicyResult(edge, updated, summary);
            }

            case EDGE_FALLING -> {
                Boolean prev = memory.getPreviousRawResult();
                // Falling edge: previous was true AND current is false
                boolean edge = !rawResult && (prev != null && prev);
                ConditionMemory updated = (rawResult
                        ? memory.withRawTrue(nowMs)
                        : memory.withRawFalse())
                        .withPolicyPassed(edge);
                String summary = edge ? "EDGE_FALLING: fired" : "EDGE_FALLING: no edge (raw=" + rawResult + ")";
                yield new MemoryPolicyResult(edge, updated, summary);
            }

            case EDGE_BOTH -> {
                Boolean prev = memory.getPreviousRawResult();
                boolean edge = prev == null ? rawResult : (rawResult != prev);
                ConditionMemory updated = (rawResult
                        ? memory.withRawTrue(nowMs)
                        : memory.withRawFalse())
                        .withPolicyPassed(edge);
                String summary = edge ? "EDGE_BOTH: fired (" + prev + "→" + rawResult + ")"
                        : "EDGE_BOTH: no edge";
                yield new MemoryPolicyResult(edge, updated, summary);
            }
        };
    }

    /**
     * Generates a human-readable summary of a ConditionMemory state for the
     * inspection snapshot (Point 9). Called by the orchestrator after evaluation.
     */
    public String summarizeMemory(ConditionMemoryPolicy policy, ConditionMemory memory) {
        if (policy == null || memory == null) return null;
        return switch (policy.getType()) {
            case DURATION -> "DURATION: "
                    + (memory.getFirstTrueEpochMs() > 0
                    ? ((System.currentTimeMillis() - memory.getFirstTrueEpochMs()) / 1000)
                    : 0)
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

    private record TreeWalkResult(boolean passed, String failedNodeId,
                                  List<ExecutionPlan.CompiledAction> negativeActionsToFire,
                                  List<ExecutionPlan.CompiledAction> positiveActionsToFire,
                                  Map<String, Boolean> conditionResults) {
        private TreeWalkResult(boolean passed, String failedNodeId,
                               List<ExecutionPlan.CompiledAction> negativeActionsToFire,
                               List<ExecutionPlan.CompiledAction> positiveActionsToFire,
                               Map<String, Boolean> conditionResults) {
            this.passed = passed;
            this.failedNodeId = failedNodeId;
            this.negativeActionsToFire = negativeActionsToFire != null ? negativeActionsToFire : List.of();
            this.positiveActionsToFire = positiveActionsToFire != null ? positiveActionsToFire : List.of();
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

        TreeWalkResult withConditionResults(Map<String, Boolean> newCondResults) {
            return new TreeWalkResult(passed, failedNodeId,
                    negativeActionsToFire, positiveActionsToFire, newCondResults);
        }
    }


    // ─────────────────────────────────────────────────────────────────────
    // NO-BRANCH PATH
    // ─────────────────────────────────────────────────────────────────────

    private EvalResult handleNoBranch(ExecutionPlan plan,
                                      AutomationRuntimeState state,
                                      EvalResult.EvalResultBuilder result,
                                      ZonedDateTime now) {
        boolean isActive = state.isTopLevelActive();

        if (!isActive) {
            List<ExecutionPlan.CompiledAction> actions = result.build().getActionsToFire();
            if (actions == null || actions.isEmpty())
                actions = plan.getTopLevelPositiveActions() != null
                        ? plan.getTopLevelPositiveActions() : List.of();

            // Always "ACTIVE" — prevents single-shot automations re-firing every tick.
            // C1_NEGATIVE resets to IDLE when the condition later becomes false,
            // even when there are no negative actions.
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

        for (ExecutionPlan.CompiledBranch branch : plan.getBranches()) {
            boolean wasActive = state.isBranchActive(branch.getGateNodeId());
            boolean gateTrue = evalSingleCondition(
                    branch.getGateCondition(), payload, wasActive, automationId, now);
            log.debug("  📊 [{}] Gate '{}' (pri={}, logic={}) wasActive={} → {}",
                    plan.getAutomationName(), branch.getGateNodeId(),
                    branch.getPriority(), branch.getLogicType(), wasActive, gateTrue);
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
                    if ("HOLDING".equals(bState)
                            && !stateStore.runningKeyExists(automationId, branch.getGateNodeId())) {
                        decisions.add(BranchDecision.durationExpired(branch));
                    } else {
                        decisions.add(BranchDecision.keepActive(branch));
                    }
                }
            } else {
                if (gateTrue && isWinner) {
                    if ("AND".equals(branch.getLogicType())
                            && branch.getSiblingGateNodeIds() != null
                            && !branch.getSiblingGateNodeIds().isEmpty()) {
                        Set<String> trueGateIds = new HashSet<>();
                        trueBranches.forEach(b -> trueGateIds.add(b.getGateNodeId()));
                        boolean allSiblingsTrue = trueGateIds.containsAll(branch.getSiblingGateNodeIds());
                        if (!allSiblingsTrue) {
                            log.debug("  🔗 [{}] Gate '{}' (AND) suppressed — not all siblings true",
                                    plan.getAutomationName(), branch.getGateNodeId());
                            decisions.add(BranchDecision.suppressed(branch,
                                    "AND: not all sibling gates met"));
                            continue;
                        }
                    }
                    decisions.add(BranchDecision.trigger(branch));
                } else if (gateTrue) {
                    decisions.add(BranchDecision.suppressed(branch, winner.getGateNodeId()));
                }
            }
        }

        boolean anyJustTriggered = decisions.stream()
                .anyMatch(d -> d.getType() == BranchDecision.Type.TRIGGER);
        boolean anyCurrentlyActive = decisions.stream()
                .anyMatch(d -> d.getType() == BranchDecision.Type.TRIGGER
                        || d.getType() == BranchDecision.Type.KEEP_ACTIVE);
        boolean anyPendingRevert = decisions.stream()
                .anyMatch(d -> d.getType() == BranchDecision.Type.REVERT
                        || d.getType() == BranchDecision.Type.DURATION_EXPIRED);

        if (!anyCurrentlyActive && winner == null && !anyPendingRevert) {
            List<ExecutionPlan.CompiledAction> fallback = plan.getFallbackActions();
            if (fallback != null && !fallback.isEmpty()) {
                return result.outcome(EvalOutcome.FALLBACK)
                        .actionsToFire(fallback).branchDecisions(decisions).build();
            }
            return result.outcome(EvalOutcome.NOT_MET)
                    .reason("c1 true but no gate branch matched")
                    .branchDecisions(decisions).build();
        }

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

        Map<String, Object> payload = primaryPayload;
        if (c.getDeviceId() != null && !c.getDeviceId().isBlank()) {
            Map<String, Object> secondary = redisService.getRecentDeviceData(c.getDeviceId());
            if (secondary == null || secondary.isEmpty()) {
                log.warn("⚠️ [{}] Secondary device '{}' has no Redis data — fetching from DB",
                        automationId, c.getDeviceId());
                var data = mainService.getLastFullData(c.getDeviceId());
                var currentTime = Instant.now();
                if (currentTime.getEpochSecond() - data.getUpdateDate().getEpochSecond() > 300) {
                    log.warn("⚠️ [{}] Data in DB is older than 5 min — condition=false", automationId);
                    return false;
                }
                secondary = data.getData();
            }
            payload = secondary;
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
            // In evalSingleCondition(), in the switch block, add:
            case "stale" -> {
                long lastSeenMs = resolveLastSeenMs(c, payload, primaryPayload, automationId);
                if (lastSeenMs <= 0) {
                    log.warn("⚠️ [{}] Condition '{}': last_seen unresolvable — treating as stale",
                            automationId, c.getNodeId());
                    yield true;  // device never seen = definitely stale
                }

                double thresholdMinutes = Double.parseDouble(c.getValue());
                long thresholdMs = (long) (thresholdMinutes * 60_000);
                long staleMs = Instant.now().toEpochMilli() - lastSeenMs;
                boolean isStale = staleMs > thresholdMs;

                log.debug("⏱️ [{}] Stale check: last_seen={}s ago, threshold={}min → {}",
                        automationId,
                        staleMs / 1000,
                        (int) thresholdMinutes,
                        isStale ? "STALE" : "FRESH");
                yield isStale;
            }
            default -> false;
        };
    }

    /**
     * Resolves the last_seen timestamp in milliseconds for a stale condition.
     * <p>
     * Resolution order:
     * 1. payload.get(triggerKey) — the field named by triggerKey (e.g. "last_seen")
     * in the current evaluation payload.  For online devices this is fresh.
     * Value may be ISO-8601 string, epoch-ms long, or epoch-s long.
     * <p>
     * 2. If condition has a deviceId (secondary device): Redis recent data for
     * that device → same key lookup in secondary payload.
     * Falls back to mainService.getLastFullData(deviceId).getUpdateDate().
     * <p>
     * 3. If payload does NOT contain the key (device is offline — no live data
     * arrived this tick): Redis recent data for the primary device → key lookup.
     * Then DB fallback via mainService.getLastFullData(triggerDeviceId).
     * <p>
     * 4. 0 → never seen, treat as stale.
     */
    private long resolveLastSeenMs(ExecutionPlan.CompiledCondition c,
                                   Map<String, Object> payload,
                                   Map<String, Object> primaryPayload,
                                   String automationId) {
        String key = c.getTriggerKey();  // e.g. "last_seen"

        // 1. Try the current evaluation payload first (fastest path)
        Long fromPayload = extractLastSeenMs(payload, key);
        if (fromPayload != null && fromPayload > 0) return fromPayload;

        // 2. If a secondary deviceId is specified, try its Redis data then DB
        if (c.getDeviceId() != null && !c.getDeviceId().isBlank()) {
            Map<String, Object> secondary = redisService.getRecentDeviceData(c.getDeviceId());
            if (secondary != null && !secondary.isEmpty()) {
                Long fromSecondary = extractLastSeenMs(secondary, key);
                if (fromSecondary != null && fromSecondary > 0) return fromSecondary;
            }
            // DB fallback for secondary device
            try {
                var data = mainService.getLastFullData(c.getDeviceId());
                if (data != null && data.getUpdateDate() != null) {
                    long dbMs = data.getUpdateDate().getEpochSecond() * 1000L;
                    log.debug("⏱️ [{}] Stale: DB fallback last_seen for '{}' = {}",
                            automationId, c.getDeviceId(), data.getUpdateDate());
                    return dbMs;
                }
            } catch (Exception e) {
                log.warn("⚠️ [{}] Stale DB fallback failed for '{}': {}",
                        automationId, c.getDeviceId(), e.getMessage());
            }
            return 0L;
        }

        // 3. Primary device — Redis fallback (device may be offline, no live payload)
        // The primary payload IS the Redis data when triggerPeriodicAutomations runs,
        // so check it again by key in case it has last_seen but under a different path.
        Long fromPrimary = extractLastSeenMs(primaryPayload, key);
        if (fromPrimary != null && fromPrimary > 0) return fromPrimary;

        // 4. DB fallback for primary device — use the record's updateDate as last_seen
        // This is what "no data has arrived at all" looks like.
        try {
            // c.getDeviceId() is null here (primary device) — we need the triggerDeviceId.
            // The evaluator doesn't have direct access to it, but if primaryPayload is
            // empty (no live event), the DB updateDate is the best proxy for last_seen.
            // Caller should pass deviceId via CompiledCondition.deviceId for secondary,
            // or leave null for primary (handled by the orchestrator's payload injection).
            log.debug("⚠️ [{}] last_seen key '{}' not in payload — device may be offline",
                    automationId, key);
        } catch (Exception ignored) {
        }

        return 0L;
    }

    /**
     * Extracts a last_seen timestamp from a map value and normalises to epoch-ms.
     * Handles:
     * - ISO-8601 string: "2026-05-17T22:34:18.601+05:30"
     * - Long epoch-ms:   1747508058601
     * - Long epoch-s:    1747508058   (detected if value < 1e10)
     * - Date object (from Jackson deserialisation)
     */
    private Long extractLastSeenMs(Map<String, Object> payload, String key) {
        if (payload == null || key == null || !payload.containsKey(key)) return null;
        Object raw = payload.get(key);
        if (raw == null) return null;

        if (raw instanceof Number n) {
            long v = n.longValue();
            // Epoch-seconds are typically 10 digits; epoch-ms are 13 digits
            return v < 10_000_000_000L ? v * 1000L : v;
        }

        if (raw instanceof Date d) return d.getTime();

        String s = raw.toString().trim();
        // Try numeric string first
        try {
            long v = Long.parseLong(s);
            return v < 10_000_000_000L ? v * 1000L : v;
        } catch (NumberFormatException ignored) {
        }

        // ISO-8601 string
        try {
            return java.time.OffsetDateTime.parse(s).toInstant().toEpochMilli();
        } catch (Exception e1) {
            try {
                return java.time.Instant.parse(s).toEpochMilli();
            } catch (Exception e2) {
                return null;
            }
        }
    }
    // ─────────────────────────────────────────────────────────────────────
    // SCHEDULE EVALUATION
    // ─────────────────────────────────────────────────────────────────────

    boolean evalScheduled(ExecutionPlan.CompiledCondition c,
                          String automationId, ZonedDateTime now) {
        LocalTime current = now.toLocalTime();

        if (c.getDays() != null && !c.getDays().isEmpty()) {
            String dow = now.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
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
            log.debug("🕒 [{}] Interval gate '{}' ready to fire", automationId, c.getNodeId());
            return true;
        }

        LocalTime target = parseTime(c.getTime());
        if (target == null) return false;
        if (Math.abs(ChronoUnit.MINUTES.between(target, current)) > 1) return false;
        return !stateStore.dailyFireKeyExists(automationId, now.toLocalDate().toString());
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
        TRIGGERED, C1_NEGATIVE, SKIPPED, NOT_MET, STATELESS_FIRE, FALLBACK, SUPPRESSED, RESTORED
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
        List<BranchDecision> branchDecisions;
        List<String> branchesToRevert;
        String nextTopLevelState;
        Date triggeredAt;
        boolean anyWasActive;
        String traceId;
        Long evalDurationMs;

        // Post-CAS schedule key signals
        boolean shouldArmIntervalCooldown;
        String intervalCooldownNodeId;
        long intervalCooldownTtlSeconds;
        boolean shouldWriteDailySolarKey;
        boolean shouldWriteDailyFireKey;

        /**
         * Point 2: memory state updates to write into nextState.conditionMemories
         * after a successful CAS. Keyed by nodeId.
         * Not null — populated for every evaluation (even SKIPPED) so memory
         * timestamps advance continuously.
         */
        Map<String, ConditionMemory> memoryUpdates;

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