package dev.automata.automata.automation_engine.evaluator;

import dev.automata.automata.automation_engine.ConditionMemoryPolicy;
import dev.automata.automata.automation_engine.EvalResult;
import dev.automata.automata.automation_engine.ExecutionPlan;

import java.util.Map;

/**
 * Moved out of AutomationOrchestrator — verbatim logic.
 */
public final class TriggerDescriptionBuilder {
    private TriggerDescriptionBuilder() {
    }

    public static String build(EvalResult result, ExecutionPlan plan) {
        if (plan.getConditionTree() == null || result.getConditionResults() == null) return null;
        Map<String, Boolean> condResults = result.getConditionResults();

        for (ExecutionPlan.CompiledConditionNode node : plan.getConditionTree()) {
            if (!Boolean.TRUE.equals(condResults.get(node.getNodeId()))) continue;
            if (node.getPositiveActions() == null || node.getPositiveActions().isEmpty()) continue;
            return describeCondition(node);
        }
        return null;
    }

    private static String describeCondition(ExecutionPlan.CompiledConditionNode node) {
        ExecutionPlan.CompiledCondition c = node.getCondition();
        if (c == null) return null;

        return switch (c.getConditionType()) {
            case "scheduled" -> switch (c.getScheduleType() != null ? c.getScheduleType() : "") {
                case "range" -> String.format("scheduled window %s–%s", c.getFromTime(), c.getToTime());
                case "solar" -> String.format("%s%s",
                        c.getSolarType() != null ? c.getSolarType() : "solar event",
                        c.getOffsetMinutes() != 0 ? " (" + c.getOffsetMinutes() + "min offset)" : "");
                case "interval" -> String.format("will run for %dmin", c.getDurationMinutes());
                case "at" -> String.format("scheduled time %s", c.getTime());
                default -> "schedule condition";
            };
            case "range" -> node.hasMemoryPolicy()
                    && node.getMemoryPolicy().getType() == ConditionMemoryPolicy.MemoryType.DURATION
                    ? String.format("%s in range %s–%s for %ds",
                    c.getTriggerKey(), c.getAbove(), c.getBelow(), node.getMemoryPolicy().getRequiredDurationSeconds())
                    : String.format("%s in range %s–%s", c.getTriggerKey(), c.getAbove(), c.getBelow());
            case "above" -> String.format("%s above %s", c.getTriggerKey(), c.getValue());
            case "below" -> String.format("%s below %s", c.getTriggerKey(), c.getValue());
            case "equal" -> String.format("%s = %s", c.getTriggerKey(), c.getValue());
            case "stale" -> String.format("%s stale (>%smin)", c.getTriggerKey(), c.getValue());
            default -> c.getConditionType();
        };
    }
}