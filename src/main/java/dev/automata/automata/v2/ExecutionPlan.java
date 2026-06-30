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
 * 1. CompiledBranch and branches removed entirely.
 * The condition tree is now the single execution path.
 * Fan-out (OR) is expressed by a node having multiple positiveChildNodeIds.
 * Sequential (AND) is expressed by a node having exactly one positiveChildNodeId.
 * <p>
 * 2. CompiledConditionNode gains fanoutMode ("ALL" | "FIRST_MATCH").
 * ALL  = evaluate every positive child independently, collect all passing actions.
 * FIRST_MATCH = stop at the first child that passes (exclusive OR).
 * Defaults to ALL when null.
 * <p>
 * 3. hasBranches() removed. hasConditionTree() is the only relevant check.
 * <p>
 * 4. triggerCoalition (Point 1) unchanged.
 * 5. ConditionMemoryPolicy (Point 2) unchanged.
 */
@Value
@Builder(toBuilder = true)
public class ExecutionPlan {

    public static final int CURRENT_SCHEMA_VERSION = 5;   // bumped from 4

    // ── Identity ──────────────────────────────────────────────────────────
    String automationId;
    String automationName;
    String triggerDeviceId;
    int schemaVersion;
    Date compiledAt;
    String homeId;

    // ── Coalition ─────────────────────────────────────────────────────────
    TriggerCoalition triggerCoalition;

    // ── Condition tree ────────────────────────────────────────────────────
    List<CompiledConditionNode> conditionTree;
    List<String> rootConditionNodeIds;

    // ── Action groups ─────────────────────────────────────────────────────
    List<CompiledAction> statelessActions;
    List<CompiledAction> fallbackActions;
    List<CompiledAction> informationalActions;
    List<CompiledAction> topLevelPositiveActions;
    List<CompiledAction> topLevelNegativeActions;

    // ── Helpers ───────────────────────────────────────────────────────────

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

        /**
         * Actions fired when this node's condition passes.
         */
        List<CompiledAction> positiveActions;

        /**
         * Actions fired when this node transitions from active → failing.
         */
        List<CompiledAction> negativeActions;

        /**
         * IDs of child condition nodes to evaluate when this node passes.
         * Empty → leaf node (fire positiveActions and return).
         * Exactly one → AND chain (child must also pass for the tree to continue).
         * More than one → OR fan-out (all children evaluated independently).
         */
        List<String> positiveChildNodeIds;

        /**
         * IDs of child condition nodes to evaluate when this node fails.
         * Rarely used — most negative paths just fire negativeActions.
         */
        List<String> negativeChildNodeIds;

        /**
         * True when this node has negative actions and therefore needs
         * its active/idle state tracked in AutomationRuntimeState.nodeStates.
         */
        boolean stateful;

        /**
         * Optional memory policy. When non-null the evaluator wraps the raw
         * evalSingleCondition result through applyMemoryPolicy().
         */
        ConditionMemoryPolicy memoryPolicy;

        /**
         * Controls fan-out behaviour when positiveChildNodeIds.size() > 1.
         * "ALL"         → evaluate every child, collect actions from all that pass.
         * "FIRST_MATCH" → stop at the first child that passes (exclusive OR).
         * null          → treated as "ALL".
         */
        String fanoutMode;
        boolean firstMatch;
        boolean fanout;

        public boolean hasMemoryPolicy() {
            return memoryPolicy != null;
        }

        /**
         * True when this node fans out to multiple OR branches.
         */
//        public boolean isFanout() {
//            return positiveChildNodeIds != null && positiveChildNodeIds.size() > 1;
//        }

        /**
         * True when FIRST_MATCH fan-out mode is active.
         */
//        public boolean isFirstMatch() {
//            return "FIRST_MATCH".equals(fanoutMode);
//        }
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