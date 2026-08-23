package dev.automata.automata.automation_engine.condition_operator_strategy;

import dev.automata.automata.automation_engine.ExecutionPlan;
import org.springframework.stereotype.Component;

@Component
public class AboveOperator implements IConditionOperator {
    public String type() {
        return "above";
    }

    public boolean evaluate(ExecutionPlan.CompiledCondition c, double v, String raw, boolean wasActive) {
        double threshold = Double.parseDouble(c.getValue());
        double buf = Math.max(1.0, Math.abs(threshold) * 0.02);
        return wasActive ? v > (threshold - buf) : v > threshold;
    }
}

