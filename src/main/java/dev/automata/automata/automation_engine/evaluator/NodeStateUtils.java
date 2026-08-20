package dev.automata.automata.automation_engine.evaluator;

import dev.automata.automata.automation_engine.ExecutionPlan;
import dev.automata.automata.automation_engine.enums.NodeState;
import dev.automata.automata.dto.AutomationRuntimeState;

import java.util.Map;

public final class NodeStateUtils {
    private NodeStateUtils() {
    }

    /**
     * Sets each stateful node's active flag strictly according to whether it
     * was walked and what it evaluated to THIS tick. Verbatim from the old
     * AutomationOrchestrator.applyPerNodeActiveFlags().
     */
    public static void applyPerNodeActiveFlags(AutomationRuntimeState next,
                                               ExecutionPlan plan,
                                               Map<String, Boolean> conditionResults) {
        if (plan.getConditionTree() == null || conditionResults == null) return;
        for (ExecutionPlan.CompiledConditionNode node : plan.getConditionTree()) {
            if (!node.isStateful()) continue;
            Boolean walkedResult = conditionResults.get(node.getNodeId());
            if (walkedResult == null) continue; // not walked this tick — leave as-is
            next.setNodeState(node.getNodeId(), walkedResult ? NodeState.ACTIVE : NodeState.IDLE);
        }
    }
}
