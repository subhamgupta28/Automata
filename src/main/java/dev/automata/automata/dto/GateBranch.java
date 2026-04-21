package dev.automata.automata.dto;

import dev.automata.automata.model.Automation;

import java.util.List;

/**
 * Resolved at evaluation time — never persisted.
 * Represents one operator → gate-condition branch in the automation graph.
 */
public record GateBranch(
        Automation.Operator operator,           // carries priority
        Automation.Condition gateCondition,     // the condition downstream of the operator
        List<Automation.Action> positiveActions,
        List<Automation.Action> negativeActions
) {
    /**
     * Convenience — gate condition node id
     */
    public String gateNodeId() {
        return gateCondition.getNodeId();
    }

    /**
     * Convenience — operator node id
     */
    public String operatorNodeId() {
        return operator.getNodeId();
    }

    /**
     * Priority from the operator; higher = wins conflict
     */
    public int priority() {
        return operator.getPriority();
    }
}
