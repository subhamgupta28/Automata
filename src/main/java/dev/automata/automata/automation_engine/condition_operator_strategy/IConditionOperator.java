package dev.automata.automata.automation_engine.condition_operator_strategy;

import dev.automata.automata.automation_engine.ExecutionPlan;

public interface IConditionOperator {
    String type(); // "above", "below", "range", "equal"

    boolean evaluate(ExecutionPlan.CompiledCondition c, double numericValue, String rawValue, boolean wasActive);
}
