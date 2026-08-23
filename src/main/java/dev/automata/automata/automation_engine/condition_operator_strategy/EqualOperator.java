package dev.automata.automata.automation_engine.condition_operator_strategy;

import dev.automata.automata.automation_engine.ExecutionPlan;
import org.springframework.stereotype.Component;

@Component
public class EqualOperator implements IConditionOperator {
    public String type() {
        return "equal";
    }

    public boolean evaluate(ExecutionPlan.CompiledCondition c, double v, String raw, boolean wasActive) {
        return c.getValue().equals(raw);
    }
}
