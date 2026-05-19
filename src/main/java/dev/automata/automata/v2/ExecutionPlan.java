package dev.automata.automata.v2;

import lombok.Builder;
import lombok.Value;

import java.util.Date;
import java.util.List;

/**
 * Immutable compiled representation of one Automation.
 * <p>
 * Changes vs previous version
 * ───────────────────────────
 * 1. triggerCoalition (Point 1)
 * Replaces the implicit single-trigger model. When non-null, the orchestrator
 * runs CoalitionGuard before calling the evaluator. null = legacy single-trigger.
 * <p>
 * 2. CompiledConditionNode.memoryPolicy (Point 2)
 * Optional per-node memory policy. When non-null, the evaluator wraps the raw
 * evalSingleCondition result through applyMemoryPolicy() before using it.
 * Memory state is stored in AutomationRuntimeState.conditionMemories.
 */
@Value
@Builder(toBuilder = true)
public class ExecutionPlan {

    public static final int CURRENT_SCHEMA_VERSION = 4;   // bumped from 3

    // ── Identity ──────────────────────────────────────────────────────────
    String automationId;
    String automationName;
    String triggerDeviceId;          // legacy — still populated for backward compat
    int schemaVersion;
    Date compiledAt;

    // ── Point 1: multi-trigger coalition ──────────────────────────────────
    // null → legacy single-trigger path (triggerDeviceId used, no quorum check)
    TriggerCoalition triggerCoalition;

    // ── Condition tree ────────────────────────────────────────────────────
    List<CompiledConditionNode> conditionTree;
    List<String> rootConditionNodeIds;

    // ── Gate branches ─────────────────────────────────────────────────────
    List<CompiledBranch> branches;

    // ── Action groups ─────────────────────────────────────────────────────
    List<CompiledAction> statelessActions;
    List<CompiledAction> fallbackActions;
    List<CompiledAction> informationalActions;
    List<CompiledAction> topLevelPositiveActions;
    List<CompiledAction> topLevelNegativeActions;

    // ── Helpers ───────────────────────────────────────────────────────────

    public boolean hasBranches() {
        return branches != null && !branches.isEmpty();
    }

    public boolean hasConditionTree() {
        return conditionTree != null && !conditionTree.isEmpty();
    }

    public boolean hasTopLevelNegativeActions() {
        return topLevelNegativeActions != null && !topLevelNegativeActions.isEmpty();
    }

    public boolean hasCoalition() {
        return triggerCoalition != null;
    }

    public boolean hasDataDrivenTrigger() {
        if (conditionTree != null) {
            return conditionTree.stream()
                    .anyMatch(n -> n.getCondition() != null
                            && !"scheduled".equals(n.getCondition().getConditionType()));
        }
        return true;
    }


    // ─────────────────────────────────────────────────────────────────────
    // COMPILED CONDITION NODE
    // ─────────────────────────────────────────────────────────────────────

    @Value
    @Builder
    public static class CompiledConditionNode {
        String nodeId;
        CompiledCondition condition;
        List<CompiledAction> positiveActions;
        List<CompiledAction> negativeActions;
        List<String> positiveChildNodeIds;
        List<String> negativeChildNodeIds;
        boolean stateful;

        /**
         * Point 2: optional memory policy.
         * null → no memory behaviour (raw evalSingleCondition result used directly).
         * non-null → result is post-processed through applyMemoryPolicy() in the evaluator.
         */
        ConditionMemoryPolicy memoryPolicy;

        public boolean hasMemoryPolicy() {
            return memoryPolicy != null;
        }
    }


    // ─────────────────────────────────────────────────────────────────────
    // COMPILED BRANCH
    // ─────────────────────────────────────────────────────────────────────

    @Value
    @Builder
    public static class CompiledBranch {
        String gateNodeId;
        int priority;
        String logicType;
        List<String> siblingGateNodeIds;
        CompiledCondition gateCondition;
        List<CompiledAction> positiveActions;
        List<CompiledAction> negativeActions;

        public boolean hasDuration() {
            return gateCondition != null && gateCondition.getDurationMinutes() > 0;
        }
    }


    // ─────────────────────────────────────────────────────────────────────
    // COMPILED CONDITION
    // ─────────────────────────────────────────────────────────────────────

    @Value
    @Builder
    public static class CompiledCondition {
        String nodeId;
        String conditionType;
        String triggerKey;
        String deviceId;
        String value;
        String above;
        String below;
        boolean isExact;
        String scheduleType;
        String fromTime;
        String toTime;
        String time;
        List<String> days;
        String solarType;
        int offsetMinutes;
        int intervalMinutes;
        int durationMinutes;
        String valueType;
    }


    // ─────────────────────────────────────────────────────────────────────
    // COMPILED ACTION
    // ─────────────────────────────────────────────────────────────────────

    @Value
    @Builder
    public static class CompiledAction {
        String nodeId;
        String deviceId;
        String deviceType;
        String key;
        String data;
        int order;
        int delaySeconds;
        String name;
    }
}