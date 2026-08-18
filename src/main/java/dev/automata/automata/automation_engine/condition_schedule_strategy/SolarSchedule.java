package dev.automata.automata.automation_engine.condition_schedule_strategy;

import dev.automata.automata.automation_engine.AutomationStateStore;
import dev.automata.automata.automation_engine.ExecutionPlan;
import dev.automata.automata.automation_engine.solar_api.ISolarTimeProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Component
@Slf4j
@RequiredArgsConstructor
public class SolarSchedule implements IScheduleEvaluator {
    private final ISolarTimeProvider solarTimeProvider;
    private final AutomationStateStore stateStore;
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    public String scheduleType() {
        return "solar";
    }

    public boolean evaluate(ExecutionPlan.CompiledCondition c, String automationId, ZonedDateTime now) {
        LocalDate today = LocalDate.now(IST);
        LocalTime current = now.toLocalTime();
        Optional<LocalTime> solar = solarTimeProvider.getSunTime(c.getSolarType(), today);
        if (solar.isEmpty()) {
            log.error("Solar time not found {}", c.getSolarType());
            return false;
        }
        LocalTime adjusted = solar.get().plusMinutes(c.getOffsetMinutes());
        if (Math.abs(ChronoUnit.MINUTES.between(adjusted, current)) > 3) return false;
        return !stateStore.dailySolarKeyExists(automationId, now.toLocalDate().toString());
    }
}
