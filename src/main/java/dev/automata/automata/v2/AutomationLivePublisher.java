package dev.automata.automata.v2;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.automata.automata.dto.AutomationRuntimeState;
import dev.automata.automata.dto.ConditionMemory;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Publishes live automation evaluation events over STOMP WebSocket.
 * <p>
 * Bug fixes (this version)
 * ─────────────────────────
 * BUG — negativeChildIds was populated from getPositiveChildNodeIds() (copy-paste
 * of the line above it), so the inspector UI showed identical positive/negative
 * child lists for every node — actively misleading for anyone debugging branch
 * structure through the dashboard. Now correctly reads getNegativeChildNodeIds().
 * <p>
 * BUG — evalWarnings / unevaluatedNodes / skippedActions logging was entirely
 * commented out, so these diagnostics were lost forever unless someone happened
 * to have the live WebSocket inspector open at the exact tick the anomaly
 * occurred. Re-enabled at appropriate log levels so they're recoverable from
 * server logs after the fact.
 * <p>
 * Note on "unevaluatedNodes" semantics: after the AutomationOrchestrator fix
 * that folds stranded-descendant negative actions into the result and extends
 * conditionResults with explicit `false` entries for them, a node that was
 * active but not reached by the recursive walk THIS tick now shows up here as
 * evaluated=true/lastRawResult=false (correctly resolved), not as missing. This
 * field continues to correctly flag genuine anomalies — true orphans/cycles —
 * without false-positiving on ordinary AND-chain backtracking.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutomationLivePublisher {

    private final SimpMessagingTemplate stomp;

    private static final String TOPIC_PER = "/topic/automation.";
    private static final String TOPIC_ALL = "/topic/automation.all";
    private static final String TOPIC_ACTIONS = "/topic/automation.actions";


    // ─────────────────────────────────────────────────────────────────────
    // MAIN PUBLISH
    // ─────────────────────────────────────────────────────────────────────

    public void publish(ExecutionPlan plan,
                        AutomationEvaluator.EvalResult result,
                        AutomationRuntimeState nextState,
                        Map<String, Object> triggerPayload) {
        if (plan == null || result == null) return;
        String id = plan.getAutomationId();

        try {
            LiveEvalEvent event = buildFullEvent(plan, result, nextState, triggerPayload);
            stomp.convertAndSend(TOPIC_PER + id, event);
        } catch (Exception e) {
            log.warn("⚠️ Failed to publish live event for '{}': {}", id, e.getMessage());
        }

        if (isBroadcastWorthy(result.getOutcome())) {
            try {
                stomp.convertAndSend(TOPIC_ALL, buildSummary(plan, result));
            } catch (Exception e) {
                log.warn("⚠️ Failed to publish broadcast summary: {}", e.getMessage());
            }
        }
    }

    public void publishActionFired(String automationId, String automationName,
                                   ExecutionPlan.CompiledAction action,
                                   boolean success, String traceId) {
        try {
            ActionFiredEvent evt = ActionFiredEvent.builder()
                    .automationId(automationId)
                    .automationName(automationName)
                    .nodeId(action.getNodeId())
                    .deviceId(action.getDeviceId())
                    .deviceName(action.getName())
                    .key(action.getKey())
                    .data(action.getData())
                    .success(success)
                    .traceId(traceId)
                    .firedAt(new Date())
                    .build();
            stomp.convertAndSend(TOPIC_ACTIONS, evt);
            stomp.convertAndSend(TOPIC_PER + automationId + ".actions", evt);
        } catch (Exception e) {
            log.warn("⚠️ Failed to publish action fired event: {}", e.getMessage());
        }
    }


    // ─────────────────────────────────────────────────────────────────────
    // FULL EVENT BUILDER
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Bug fix (this version) — false-positive "unevaluated node" log spam.
     * <p>
     * The previous version flagged ANY node absent from conditionResults as
     * an anomaly ("check for cycle, disconnected branch, or failed parent").
     * In production this fired constantly and for nearly every automation:
     * an automation whose root (or any ancestor) is simply false on a given
     * tick — by far the MOST common case, since automations don't fire every
     * 12 seconds — leaves every descendant of that ancestor absent from
     * conditionResults purely because the AND-chain/OR-fanout walk legitimately
     * never reaches them. That is completely normal, routine behaviour, not an
     * anomaly, yet it was logged at WARN level on every such tick for every
     * such automation.
     * <p>
     * Fix: a node is only reported as a genuine anomaly when it is BOTH (a)
     * absent from this tick's conditionResults AND (b) not part of the
     * STATICALLY reachable set computed purely from the plan's structure
     * (rootConditionNodeIds + positiveChildNodeIds, regardless of any
     * condition's current value). This mirrors exactly the distinction
     * AutomationGraphValidator already makes at save time (Check 4 orphans,
     * Check 5 cycles) — a node that's part of a normal chain under a
     * currently-false ancestor is still statically reachable and therefore
     * not warned about; a node with no path from any root at all (a true
     * orphan, or one isolated by a cycle) remains correctly flagged.
     */
    private LiveEvalEvent buildFullEvent(ExecutionPlan plan,
                                         AutomationEvaluator.EvalResult result,
                                         AutomationRuntimeState nextState,
                                         Map<String, Object> triggerPayload) {

        Map<String, Boolean> condResults = result.getConditionResults() != null
                ? result.getConditionResults() : Map.of();
        Map<String, ConditionMemory> memUpdates = result.getMemoryUpdates() != null
                ? result.getMemoryUpdates() : Map.of();
        Map<String, String> nodeStateMap = nextState != null
                ? nextState.getNodeStates() : Map.of();

        Set<String> staticallyReachable = computeStaticallyReachableNodeIds(plan);

        // ── Condition node view ───────────────────────────────────────────
        List<LiveConditionNode> condNodes = new ArrayList<>();
        List<String> missingNodes = new ArrayList<>();  // in plan but not evaluated
        List<String> unevaluatedNodes = new ArrayList<>();  // genuinely unreachable
        List<String> evalWarnings = new ArrayList<>();

        if (plan.getConditionTree() != null) {
            Set<String> planNodeIds = plan.getConditionTree().stream()
                    .map(ExecutionPlan.CompiledConditionNode::getNodeId)
                    .collect(Collectors.toSet());

            Map<String, String> memSummaries = buildMemorySummaries(plan, memUpdates);

            for (ExecutionPlan.CompiledConditionNode n : plan.getConditionTree()) {
                String nodeId = n.getNodeId();
                ConditionMemory mem = memUpdates.get(nodeId);
                Boolean rawResult = condResults.get(nodeId);

                // FIX: only a genuine structural anomaly — absent from this
                // tick's walk AND not statically reachable from any root at
                // all — is reported. A node simply skipped because an
                // ancestor is currently false (the routine, constant case)
                // is statically reachable and therefore silent here.
                if (rawResult == null && !staticallyReachable.contains(nodeId)) {
                    unevaluatedNodes.add(nodeId);
                    evalWarnings.add("Node '" + nodeId + "' has no path from any root condition "
                            + "node — check for a cycle or a disconnected branch in the editor.");
                }

                // Detect plan nodes not in the condition tree (compilation gap)
                if (!planNodeIds.contains(nodeId)) {
                    missingNodes.add(nodeId);
                    evalWarnings.add("Node '" + nodeId + "' is in plan but missing from conditionTree");
                }

                String nodeState = nodeStateMap.getOrDefault(nodeId, "IDLE");
                boolean isActive = "ACTIVE".equals(nodeState) || "HOLDING".equals(nodeState);

                // Fan-out role
                String fanoutRole = null;
                if (n.isFanout()) {
                    fanoutRole = n.isFirstMatch() ? "OR_FIRST_MATCH" : "OR_ALL";
                } else if (n.getPositiveChildNodeIds() != null
                        && n.getPositiveChildNodeIds().size() == 1) {
                    fanoutRole = "AND";
                } else if (n.getPositiveChildNodeIds() == null
                        || n.getPositiveChildNodeIds().isEmpty()) {
                    fanoutRole = "LEAF";
                }

                condNodes.add(LiveConditionNode.builder()
                        .nodeId(nodeId)
                        .conditionType(n.getCondition() != null
                                ? n.getCondition().getConditionType() : null)
                        .triggerKey(n.getCondition() != null
                                ? n.getCondition().getTriggerKey() : null)
                        .deviceId(n.getCondition() != null
                                ? n.getCondition().getDeviceId() : null)
                        .scheduleType(n.getCondition() != null
                                ? n.getCondition().getScheduleType() : null)
                        .stateful(n.isStateful())
                        .nodeState(nodeState)
                        .wasActive(isActive)
                        .lastRawResult(rawResult)
                        .evaluated(rawResult != null)
                        .positiveChildIds(n.getPositiveChildNodeIds())
                        .negativeChildIds(n.getNegativeChildNodeIds())
                        .fanoutRole(fanoutRole)
                        .fanoutMode(n.getFanoutMode())
                        .hasMemoryPolicy(n.hasMemoryPolicy())
                        .memoryPolicyType(n.hasMemoryPolicy()
                                ? n.getMemoryPolicy().getType().name() : null)
                        .memorySummary(memSummaries.get(nodeId))
                        .firstTrueEpochMs(mem != null ? mem.getFirstTrueEpochMs() : 0)
                        .consecutiveTrueCount(mem != null ? mem.getConsecutiveTrueCount() : 0)
                        .previousRawResult(mem != null ? mem.getPreviousRawResult() : null)
                        .positiveActionsCount(n.getPositiveActions() != null
                                ? n.getPositiveActions().size() : 0)
                        .negativeActionsCount(n.getNegativeActions() != null
                                ? n.getNegativeActions().size() : 0)
                        .build());
            }

            // Warn if conditionResults contains node IDs not in the plan tree
            // (stale compiled plan or evaluator walking a node that was since removed)
            for (String evaluatedId : condResults.keySet()) {
                if (!planNodeIds.contains(evaluatedId)
                        && !evaluatedId.startsWith("fanout@")) {  // fanout@ are synthetic
                    evalWarnings.add("Node '" + evaluatedId
                            + "' was evaluated but is not in the compiled conditionTree"
                            + " — plan may be stale, consider re-saving the automation");
                }
            }
        }

        // ── Actions observability ─────────────────────────────────────────
        List<String> firedActionNodeIds = List.of();
        List<SkippedAction> skippedActions = new ArrayList<>();

        if (result.getActionsToFire() != null) {
            firedActionNodeIds = result.getActionsToFire().stream()
                    .map(ExecutionPlan.CompiledAction::getNodeId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        // Detect positive actions in plan that were not fired
        if (plan.getConditionTree() != null
                && result.getOutcome() == AutomationEvaluator.EvalOutcome.TRIGGERED) {
            Set<String> fired = new HashSet<>(firedActionNodeIds);
            for (ExecutionPlan.CompiledConditionNode n : plan.getConditionTree()) {
                if (n.getPositiveActions() == null) continue;
                for (ExecutionPlan.CompiledAction a : n.getPositiveActions()) {
                    if (a.getNodeId() != null && !fired.contains(a.getNodeId())) {
                        Boolean nodeResult = condResults.get(n.getNodeId());
                        String reason = nodeResult == null
                                ? "parent node was not evaluated"
                                : Boolean.FALSE.equals(nodeResult)
                                  ? "parent node condition was false"
                                  : "action was deduplicated or filtered";
                        skippedActions.add(SkippedAction.builder()
                                .nodeId(a.getNodeId())
                                .deviceId(a.getDeviceId())
                                .key(a.getKey())
                                .data(a.getData())
                                .parentNodeId(n.getNodeId())
                                .reason(reason)
                                .build());
                    }
                }
            }
        }

        // FIX: re-enabled server-side logging so these diagnostics survive
        // even when nobody has the live WebSocket inspector open at the time
        // the anomaly occurs. Levels chosen so normal operation stays quiet:
        // evalWarnings (genuine anomalies) -> WARN, skippedActions -> DEBUG.
        if (!evalWarnings.isEmpty()) {
            evalWarnings.forEach(w ->
                    log.warn("🔍 [{}][traceId={}] Observability: {}",
                            plan.getAutomationName(), result.getTraceId(), w));
        }
        if (!unevaluatedNodes.isEmpty()) {
            log.warn("🔍 [{}][traceId={}] Unevaluated nodes: {}",
                    plan.getAutomationName(), result.getTraceId(), unevaluatedNodes);
        }
        if (!skippedActions.isEmpty()) {
            log.debug("🔍 [{}][traceId={}] Skipped actions: {}",
                    plan.getAutomationName(), result.getTraceId(),
                    skippedActions.stream().map(s -> s.getNodeId() + "(" + s.getReason() + ")")
                            .collect(Collectors.joining(", ")));
        }

        return LiveEvalEvent.builder()
                .type("EVAL")
                .automationId(plan.getAutomationId())
                .automationName(plan.getAutomationName())
                .schemaVersion(plan.getSchemaVersion())
                .outcome(result.getOutcome().name())
                .c1True(result.isC1True())
                .anyWasActive(result.isAnyWasActive())
                .reason(result.getReason())
                .traceId(result.getTraceId())
                .evalDurationMs(result.getEvalDurationMs())
                .evaluatedAt(result.getEvaluatedAt())
                .topLevelState(nextState != null ? nextState.getTopLevelState() : null)
                .nodeStates(nodeStateMap)
                .triggerPayload(triggerPayload)
                .conditionNodes(condNodes)
                .missingNodes(missingNodes.isEmpty() ? null : missingNodes)
                .unevaluatedNodes(unevaluatedNodes.isEmpty() ? null : unevaluatedNodes)
                .evalWarnings(evalWarnings.isEmpty() ? null : evalWarnings)
                .coalitionLastFired(nextState != null
                        ? nextState.getTriggerMemberLastFired() : null)
                .sequenceProgress(nextState != null ? nextState.getSequenceProgress() : 0)
                .firedActionNodeIds(firedActionNodeIds)
                .skippedActions(skippedActions.isEmpty() ? null : skippedActions)
                .build();
    }

    private LiveEvalSummary buildSummary(ExecutionPlan plan,
                                         AutomationEvaluator.EvalResult result) {
        return LiveEvalSummary.builder()
                .type("SUMMARY")
                .automationId(plan.getAutomationId())
                .automationName(plan.getAutomationName())
                .outcome(result.getOutcome().name())
                .c1True(result.isC1True())
                .traceId(result.getTraceId())
                .evaluatedAt(result.getEvaluatedAt())
                .evalDurationMs(result.getEvalDurationMs())
                .build();
    }


    // ─────────────────────────────────────────────────────────────────────
    // STATIC REACHABILITY (see buildFullEvent fix javadoc)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Computes every nodeId reachable from any root by following
     * positiveChildNodeIds, treating EVERY condition as if it could
     * potentially evaluate true — this is a purely structural property of
     * the compiled plan, independent of any tick's actual payload or
     * condition values.
     * <p>
     * Used to distinguish a genuine structural anomaly (a node with no path
     * from any root at all — a true orphan, or one isolated by a cycle) from
     * the routine case where a node simply wasn't walked this tick because
     * an ancestor's condition is currently false. The former is a real
     * problem worth a WARN log; the latter happens on the vast majority of
     * ticks for the vast majority of automations and must stay silent.
     */
    private Set<String> computeStaticallyReachableNodeIds(ExecutionPlan plan) {
        if (plan.getConditionTree() == null || plan.getRootConditionNodeIds() == null) {
            return Set.of();
        }

        Map<String, ExecutionPlan.CompiledConditionNode> nodeMap = plan.getConditionTree().stream()
                .collect(Collectors.toMap(ExecutionPlan.CompiledConditionNode::getNodeId, n -> n));

        Set<String> reachable = new HashSet<>();
        Deque<String> stack = new ArrayDeque<>(plan.getRootConditionNodeIds());

        while (!stack.isEmpty()) {
            String current = stack.pop();
            if (!reachable.add(current)) continue; // already visited — cycle-safe
            ExecutionPlan.CompiledConditionNode node = nodeMap.get(current);
            if (node == null || node.getPositiveChildNodeIds() == null) continue;
            stack.addAll(node.getPositiveChildNodeIds());
        }

        return reachable;
    }


    // ─────────────────────────────────────────────────────────────────────
    // MEMORY SUMMARY
    // ─────────────────────────────────────────────────────────────────────

    private Map<String, String> buildMemorySummaries(ExecutionPlan plan,
                                                     Map<String, ConditionMemory> memUpdates) {
        if (plan.getConditionTree() == null) return Map.of();
        return plan.getConditionTree().stream()
                .filter(ExecutionPlan.CompiledConditionNode::hasMemoryPolicy)
                .filter(n -> memUpdates.containsKey(n.getNodeId()))
                .collect(Collectors.toMap(
                        ExecutionPlan.CompiledConditionNode::getNodeId,
                        n -> summarizeMemory(n.getMemoryPolicy(), memUpdates.get(n.getNodeId()))
                ));
    }

    private String summarizeMemory(ConditionMemoryPolicy policy, ConditionMemory mem) {
        if (policy == null || mem == null) return null;
        return switch (policy.getType()) {
            case DURATION -> "DURATION: "
                    + (mem.getFirstTrueEpochMs() > 0
                    ? (System.currentTimeMillis() - mem.getFirstTrueEpochMs()) / 1000 : 0)
                    + "/" + policy.getRequiredDurationSeconds() + "s";
            case CONSECUTIVE_TICKS -> "CONSECUTIVE: " + mem.getConsecutiveTrueCount() + "/" + policy.getRequiredTicks();
            case EDGE_RISING -> "EDGE_RISING: prev=" + mem.getPreviousRawResult();
            case EDGE_FALLING -> "EDGE_FALLING: prev=" + mem.getPreviousRawResult();
            case EDGE_BOTH -> "EDGE_BOTH: prev=" + mem.getPreviousRawResult();
        };
    }

    private boolean isBroadcastWorthy(AutomationEvaluator.EvalOutcome outcome) {
        return switch (outcome) {
            case TRIGGERED, C1_NEGATIVE, STATELESS_FIRE, FALLBACK, BRANCH_TRIGGERED -> true;
            case SKIPPED, NOT_MET -> false;
        };
    }


    // ─────────────────────────────────────────────────────────────────────
    // EVENT DTOs
    // ─────────────────────────────────────────────────────────────────────

    @Value
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LiveEvalEvent {
        String type;                           // always "EVAL"
        String automationId;
        String automationName;
        int schemaVersion;
        String outcome;                        // EvalOutcome.name()
        boolean c1True;
        boolean anyWasActive;
        String reason;
        String traceId;
        Long evalDurationMs;
        Date evaluatedAt;

        /**
         * Top-level IDLE / ACTIVE state of the automation.
         */
        String topLevelState;

        /**
         * Full nodeId → "IDLE"/"ACTIVE"/"HOLDING" map from AutomationRuntimeState.
         */
        Map<String, String> nodeStates;

        /**
         * Raw trigger payload that caused this evaluation.
         */
        Map<String, Object> triggerPayload;

        /**
         * One entry per condition tree node with full diagnostic data.
         */
        List<LiveConditionNode> conditionNodes;

        /**
         * Node IDs present in the compiled plan but absent from conditionResults.
         * Non-null only when anomalies are detected.
         */
        List<String> missingNodes;

        /**
         * Node IDs that are enabled in the plan but were not reached by the
         * tree walk (disconnected branch, failed parent, or cycle).
         * Non-null only when anomalies are detected.
         */
        List<String> unevaluatedNodes;

        /**
         * Human-readable warnings about anomalies detected during this evaluation.
         * Logged server-side AND sent over WebSocket.
         * Non-null only when anomalies are detected.
         */
        List<String> evalWarnings;

        Map<String, Long> coalitionLastFired;
        int sequenceProgress;

        /**
         * Node IDs of actions that were dispatched this tick.
         */
        List<String> firedActionNodeIds;

        /**
         * Actions present in the plan but not dispatched, with reason.
         * Non-null only when at least one action was skipped.
         */
        List<SkippedAction> skippedActions;
    }

    @Value
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LiveConditionNode {
        String nodeId;
        String conditionType;
        String triggerKey;
        String deviceId;
        String scheduleType;

        /**
         * true when this node has negative actions (its state is tracked).
         */
        boolean stateful;

        /**
         * Current state from AutomationRuntimeState.nodeStates.
         */
        String nodeState;

        /**
         * Convenience alias: nodeState is ACTIVE or HOLDING.
         */
        boolean wasActive;

        /**
         * Raw evaluation result from evalSingleCondition(). null if not reached.
         */
        Boolean lastRawResult;

        /**
         * false when the node was not reached by the tree walk this tick.
         */
        boolean evaluated;

        List<String> positiveChildIds;
        List<String> negativeChildIds;

        /**
         * "LEAF"          — no children, fires actions directly.
         * "AND"           — single child, sequential.
         * "OR_ALL"        — multiple children, all evaluated.
         * "OR_FIRST_MATCH"— multiple children, stops at first pass.
         */
        String fanoutRole;
        String fanoutMode;

        boolean hasMemoryPolicy;
        String memoryPolicyType;
        String memorySummary;
        long firstTrueEpochMs;
        int consecutiveTrueCount;
        Boolean previousRawResult;

        int positiveActionsCount;
        int negativeActionsCount;
    }

    @Value
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class SkippedAction {
        String nodeId;
        String deviceId;
        String key;
        String data;
        String parentNodeId;
        String reason;
    }

    @Value
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LiveEvalSummary {
        String type;
        String automationId;
        String automationName;
        String outcome;
        boolean c1True;
        String traceId;
        Date evaluatedAt;
        Long evalDurationMs;
    }

    @Value
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ActionFiredEvent {
        String automationId;
        String automationName;
        String nodeId;
        String deviceId;
        String deviceName;
        String key;
        String data;
        boolean success;
        String traceId;
        Date firedAt;
    }
}