package dev.automata.automata.automation_engine.condition_schedule_strategy;

import dev.automata.automata.automation_engine.ExecutionPlan;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.ZonedDateTime;

import static dev.automata.automata.automation_engine.helpers.ScheduleHelper.parseTime;

@Component
public class RangeSchedule implements IScheduleEvaluator {
    public String scheduleType() {
        return "range";
    }

    public boolean evaluate(ExecutionPlan.CompiledCondition c, String automationId, ZonedDateTime now) {
        LocalTime from = parseTime(c.getFromTime()), to = parseTime(c.getToTime());
        LocalTime current = now.toLocalTime();
        if (from == null || to == null) return false;
        return from.isBefore(to)
                ? !current.isBefore(from) && !current.isAfter(to)
                : !current.isBefore(from) || !current.isAfter(to);
    }
}
