package dev.automata.automata.automation_engine.condition_schedule_strategy;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class ScheduleRegistry {
    private final Map<String, IScheduleEvaluator> byType;

    public ScheduleRegistry(List<IScheduleEvaluator> operators) {
        this.byType = operators.stream().collect(Collectors.toMap(IScheduleEvaluator::scheduleType, o -> o));
    }

    public Optional<IScheduleEvaluator> find(String type) {
        return Optional.ofNullable(byType.get(type));
    }
}
