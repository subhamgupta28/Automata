package dev.automata.automata.automation_engine.condition_schedule_strategy;

import dev.automata.automata.automation_engine.AutomationStateStore;
import dev.automata.automata.automation_engine.ExecutionPlan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

@Component
@Slf4j
@RequiredArgsConstructor
public class IntervalSchedule implements IScheduleEvaluator {

    private final AutomationStateStore stateStore;

    public String scheduleType() {
        return "interval";
    }

    public boolean evaluate(ExecutionPlan.CompiledCondition c, String automationId, ZonedDateTime now) {
        if (stateStore.runningKeyExists(automationId, c.getNodeId())) return true;
        if (stateStore.intervalKeyExists(automationId, c.getNodeId())) return false;
        log.debug("🕒 [{}] Interval '{}' ready to fire", automationId, c.getNodeId());
        return true;
    }
}
