package dev.automata.automata.automation_engine.memory_policy_strategy;

import dev.automata.automata.automation_engine.ConditionMemoryPolicy;
import dev.automata.automata.automation_engine.dto.MemoryPolicyResult;
import dev.automata.automata.dto.ConditionMemory;

public interface IMemoryPolicyStrategy {
    ConditionMemoryPolicy.MemoryType type();

    MemoryPolicyResult apply(ConditionMemoryPolicy policy, boolean rawResult, ConditionMemory memory, long nowMs);

    String summarize(ConditionMemoryPolicy policy, ConditionMemory memory);
}
