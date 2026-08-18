package dev.automata.automata.automation_engine.condition_operator_strategy;

import dev.automata.automata.automation_engine.ExecutionPlan;
import org.springframework.stereotype.Component;

@Component
public class RangeOperator implements IConditionOperator {
    public String type() {
        return "range";
    }

    public boolean evaluate(ExecutionPlan.CompiledCondition c, double v, String raw, boolean wasActive) {
        double a = Double.parseDouble(c.getAbove());
        double b = Double.parseDouble(c.getBelow());
        double threshold = Double.parseDouble(c.getValue());
        double buf = Math.max(1.0, Math.abs(threshold) * 0.02);
        return wasActive ? v > (a - buf) && v < (b + buf) : v > a && v < b;
    }
}
