package dev.automata.automata.automation_engine.dto;

import dev.automata.automata.automation_engine.ExecutionPlan;

import java.util.List;
import java.util.Map;

public record TreeWalkResult(
        boolean passed,
        String failedNodeId,
        List<ExecutionPlan.CompiledAction> negativeActionsToFire,
        List<ExecutionPlan.CompiledAction> positiveActionsToFire,
        Map<String, Boolean> conditionResults
) {

    public TreeWalkResult(
            boolean passed, String failedNodeId,
            List<ExecutionPlan.CompiledAction> negativeActionsToFire,
            List<ExecutionPlan.CompiledAction> positiveActionsToFire,
            Map<String, Boolean> conditionResults
    ) {
        this.passed = passed;
        this.failedNodeId = failedNodeId;
        this.negativeActionsToFire = negativeActionsToFire != null
                ? negativeActionsToFire : List.of();
        this.positiveActionsToFire = positiveActionsToFire != null
                ? positiveActionsToFire : List.of();
        this.conditionResults = conditionResults != null ? conditionResults : Map.of();
    }

    public static TreeWalkResult failed(String nodeId,
                                        List<ExecutionPlan.CompiledAction> negActions,
                                        Map<String, Boolean> condResults) {
        return new TreeWalkResult(false, nodeId, negActions, List.of(), condResults);
    }

    public static TreeWalkResult passed(List<ExecutionPlan.CompiledAction> posActions,
                                        Map<String, Boolean> condResults) {
        return new TreeWalkResult(true, null, List.of(), posActions, condResults);
    }
}