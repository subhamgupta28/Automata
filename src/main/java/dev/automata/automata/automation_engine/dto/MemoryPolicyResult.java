package dev.automata.automata.automation_engine.dto;

import dev.automata.automata.dto.ConditionMemory;


public record MemoryPolicyResult(
        boolean passes,
        ConditionMemory updatedMemory,
        String memorySummary
) {
}
