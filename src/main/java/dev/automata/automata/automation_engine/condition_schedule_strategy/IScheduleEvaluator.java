package dev.automata.automata.automation_engine.condition_schedule_strategy;

import dev.automata.automata.automation_engine.ExecutionPlan;

import java.time.ZonedDateTime;

public interface IScheduleEvaluator {
    String scheduleType(); // "range", "solar", "interval", "at" (or null/default)

    boolean evaluate(ExecutionPlan.CompiledCondition c, String automationId, ZonedDateTime now);

}

