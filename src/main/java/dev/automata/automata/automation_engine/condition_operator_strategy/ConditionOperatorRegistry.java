package dev.automata.automata.automation_engine.condition_operator_strategy;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class ConditionOperatorRegistry {
    private final Map<String, IConditionOperator> byType;

    public ConditionOperatorRegistry(List<IConditionOperator> operators) {
        this.byType = operators.stream().collect(Collectors.toMap(IConditionOperator::type, o -> o));
    }

    public Optional<IConditionOperator> find(String type) {
        return Optional.ofNullable(byType.get(type));
    }
}


