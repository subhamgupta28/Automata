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

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Publishes live automation evaluation events over STOMP WebSocket.
 * <p>
 * Topics
 * ──────
 * /topic/automation.{id}
 * Per-automation stream — one LiveEvalEvent per execution.
 * Subscribed to by the inspector panel when a specific automation is open.
 * Payload: LiveEvalEvent (full eval result + new state snapshot)
 * <p>
 * /topic/automation.all
 * Broadcast stream — one LiveEvalSummary per execution for every automation.
 * Subscribed to by the automation list view to show live status badges.
 * Payload: LiveEvalSummary (minimal: id, name, outcome, timestamp)
 * Only published for state-changing outcomes (TRIGGERED, RESTORED,
 * C1_NEGATIVE) to avoid flooding the list on every SKIPPED tick.
 * <p>
 * Called by AutomationOrchestrator after every execution (post-CAS).
 * No-op if no subscribers — SimpMessagingTemplate silently drops if
 * no sessions are connected to the topic.
 * <p>
 * Thread safety: SimpMessagingTemplate is thread-safe.
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
    // MAIN PUBLISH — called by AutomationOrchestrator after every execution
    // ─────────────────────────────────────────────────────────────────────

    public void publish(ExecutionPlan plan,
                        AutomationEvaluator.EvalResult result,
                        AutomationRuntimeState nextState,
                        Map<String, Object> triggerPayload) {
        if (plan == null || result == null) return;
        String id = plan.getAutomationId();

        // ── Per-automation full event ──────────────────────────────────────
        try {
            LiveEvalEvent event = buildFullEvent(plan, result, nextState, triggerPayload);
            stomp.convertAndSend(TOPIC_PER + id, event);
        } catch (Exception e) {
            log.warn("⚠️ Failed to publish live event for '{}': {}", id, e.getMessage());
        }

        // ── Broadcast summary (only meaningful outcomes) ───────────────────
        if (isBroadcastWorthy(result.getOutcome())) {
            try {
                stomp.convertAndSend(TOPIC_ALL, buildSummary(plan, result));
            } catch (Exception e) {
                log.warn("⚠️ Failed to publish broadcast summary: {}", e.getMessage());
            }
        }
    }

    /**
     * Publish a per-action dispatch event so the inspector can show
     * exactly which actions fired and in what order.
     * Called by ActionDispatcher after each device command is sent.
     */
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
    // BUILDERS
    // ─────────────────────────────────────────────────────────────────────

    private LiveEvalEvent buildFullEvent(ExecutionPlan plan,
                                         AutomationEvaluator.EvalResult result,
                                         AutomationRuntimeState nextState,
                                         Map<String, Object> triggerPayload) {

        // Condition node view — merge plan metadata with runtime memory
        List<LiveConditionNode> condNodes = null;
        if (plan.getConditionTree() != null) {
            Map<String, Boolean> condResults = result.getConditionResults() != null
                    ? result.getConditionResults() : Map.of();
            Map<String, ConditionMemory> memUpdates = result.getMemoryUpdates() != null
                    ? result.getMemoryUpdates() : Map.of();
            Map<String, String> memSummaries = buildMemorySummaries(plan, memUpdates);

            condNodes = plan.getConditionTree().stream().map(n -> {
                ConditionMemory mem = memUpdates.get(n.getNodeId());
                return LiveConditionNode.builder()
                        .nodeId(n.getNodeId())
                        .conditionType(n.getCondition() != null ? n.getCondition().getConditionType() : null)
                        .triggerKey(n.getCondition() != null ? n.getCondition().getTriggerKey() : null)
                        .stateful(n.isStateful())
                        .wasActive(nextState != null && nextState.isNodeActive(n.getNodeId()))
                        .lastRawResult(condResults.get(n.getNodeId()))
                        .hasMemoryPolicy(n.hasMemoryPolicy())
                        .memoryPolicyType(n.hasMemoryPolicy() ? n.getMemoryPolicy().getType().name() : null)
                        .memorySummary(memSummaries.get(n.getNodeId()))
                        .firstTrueEpochMs(mem != null ? mem.getFirstTrueEpochMs() : 0)
                        .consecutiveTrueCount(mem != null ? mem.getConsecutiveTrueCount() : 0)
                        .build();
            }).collect(Collectors.toList());
        }

        // Branch view
        List<LiveBranchState> branchStates = null;
        if (plan.getBranches() != null && nextState != null) {
            branchStates = plan.getBranches().stream().map(b -> LiveBranchState.builder()
                    .gateNodeId(b.getGateNodeId())
                    .priority(b.getPriority())
                    .logicType(b.getLogicType())
                    .scheduleType(b.getGateCondition() != null ? b.getGateCondition().getScheduleType() : null)
                    .fromTime(b.getGateCondition() != null ? b.getGateCondition().getFromTime() : null)
                    .toTime(b.getGateCondition() != null ? b.getGateCondition().getToTime() : null)
                    .intervalMinutes(b.getGateCondition() != null ? b.getGateCondition().getIntervalMinutes() : 0)
                    .state(nextState.getBranchStateStr(b.getGateNodeId()))
                    .active(nextState.isBranchActive(b.getGateNodeId()))
                    .positiveActionsCount(b.getPositiveActions() != null ? b.getPositiveActions().size() : 0)
                    .negativeActionsCount(b.getNegativeActions() != null ? b.getNegativeActions().size() : 0)
                    .build()).collect(Collectors.toList());
        }

        // Extract action node IDs that have been triggered for execution
        List<String> executedActionNodeIds = null;
        if (result.getActionsToFire() != null) {
            executedActionNodeIds = result.getActionsToFire().stream()
                    .map(ExecutionPlan.CompiledAction::getNodeId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        return LiveEvalEvent.builder()
                .type("EVAL")
                .automationId(plan.getAutomationId())
                .automationName(plan.getAutomationName())
                .outcome(result.getOutcome().name())
                .c1True(result.isC1True())
                .anyWasActive(result.isAnyWasActive())
                .reason(result.getReason())
                .traceId(result.getTraceId())
                .evalDurationMs(result.getEvalDurationMs())
                .evaluatedAt(result.getEvaluatedAt())
                .topLevelState(nextState != null ? nextState.getTopLevelState() : null)
                .triggerPayload(triggerPayload)
                .conditionNodes(condNodes)
                .branchStates(branchStates)
                .coalitionLastFired(nextState != null ? nextState.getTriggerMemberLastFired() : null)
                .sequenceProgress(nextState != null ? nextState.getSequenceProgress() : 0)
                .executedActionNodeIds(executedActionNodeIds)
                .firedActionNodeIds(
                        result.getActionsToFire() != null
                                ? result.getActionsToFire().stream()
                                  .map(ExecutionPlan.CompiledAction::getNodeId)
                                  .filter(Objects::nonNull)
                                  .collect(Collectors.toList())
                                : List.of()
                )
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

    private Map<String, String> buildMemorySummaries(ExecutionPlan plan,
                                                     Map<String, ConditionMemory> memUpdates) {
        return plan.getConditionTree().stream()
                .filter(ExecutionPlan.CompiledConditionNode::hasMemoryPolicy)
                .filter(n -> memUpdates.containsKey(n.getNodeId()))
                .collect(Collectors.toMap(
                        ExecutionPlan.CompiledConditionNode::getNodeId,
                        n -> summarizeMemory(n.getMemoryPolicy(),
                                memUpdates.get(n.getNodeId()))
                ));
    }

    private String summarizeMemory(ConditionMemoryPolicy policy, ConditionMemory mem) {
        if (policy == null || mem == null) return null;
        return switch (policy.getType()) {
            case DURATION -> "DURATION: "
                    + (mem.getFirstTrueEpochMs() > 0
                    ? (System.currentTimeMillis() - mem.getFirstTrueEpochMs()) / 1000
                    : 0)
                    + "/" + policy.getRequiredDurationSeconds() + "s";
            case CONSECUTIVE_TICKS -> "CONSECUTIVE: " + mem.getConsecutiveTrueCount() + "/" + policy.getRequiredTicks();
            case EDGE_RISING -> "EDGE_RISING: prev=" + mem.getPreviousRawResult();
            case EDGE_FALLING -> "EDGE_FALLING: prev=" + mem.getPreviousRawResult();
            case EDGE_BOTH -> "EDGE_BOTH: prev=" + mem.getPreviousRawResult();
        };
    }

    private boolean isBroadcastWorthy(AutomationEvaluator.EvalOutcome outcome) {
        return switch (outcome) {
            case TRIGGERED, RESTORED, C1_NEGATIVE, STATELESS_FIRE, FALLBACK -> true;
            case SKIPPED, NOT_MET, SUPPRESSED -> false;
        };
    }


    // ─────────────────────────────────────────────────────────────────────
    // EVENT DTOs
    // ─────────────────────────────────────────────────────────────────────

    @Value
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LiveEvalEvent {
        String type;                          // always "EVAL"
        String automationId;
        String automationName;
        String outcome;                       // EvalOutcome.name()
        boolean c1True;
        boolean anyWasActive;
        String reason;
        String traceId;
        Long evalDurationMs;
        Date evaluatedAt;
        String topLevelState;
        Map<String, Object> triggerPayload;
        List<LiveConditionNode> conditionNodes;
        List<LiveBranchState> branchStates;
        Map<String, Long> coalitionLastFired;
        int sequenceProgress;
        List<String> executedActionNodeIds;
        List<String> firedActionNodeIds;
    }

    @Value
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LiveConditionNode {
        String nodeId;
        String conditionType;
        String triggerKey;
        boolean stateful;
        boolean wasActive;
        Boolean lastRawResult;
        boolean hasMemoryPolicy;
        String memoryPolicyType;
        String memorySummary;
        long firstTrueEpochMs;
        int consecutiveTrueCount;
    }

    @Value
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LiveBranchState {
        String gateNodeId;
        int priority;
        String logicType;
        String scheduleType;
        String fromTime;
        String toTime;
        int intervalMinutes;
        String state;
        boolean active;
        int positiveActionsCount;
        int negativeActionsCount;
    }

    @Value
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class LiveEvalSummary {
        String type;                          // always "SUMMARY"
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