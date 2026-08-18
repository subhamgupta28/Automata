package dev.automata.automata.automation_engine.memory_policy_strategy;

import dev.automata.automata.automation_engine.ConditionMemoryPolicy;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Component

public class MemoryPolicyRegistry {
    private final Map<ConditionMemoryPolicy.MemoryType, IMemoryPolicyStrategy> memoryType;

    public MemoryPolicyRegistry(List<IMemoryPolicyStrategy> operators) {
        this.memoryType = operators.stream().collect(Collectors.toMap(IMemoryPolicyStrategy::type, o -> o));
    }

    public Optional<IMemoryPolicyStrategy> find(ConditionMemoryPolicy.MemoryType type) {
        return Optional.ofNullable(memoryType.get(type));
    }

}
