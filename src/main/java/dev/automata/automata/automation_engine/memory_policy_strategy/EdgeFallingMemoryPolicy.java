package dev.automata.automata.automation_engine.memory_policy_strategy;

import dev.automata.automata.automation_engine.ConditionMemoryPolicy;
import dev.automata.automata.automation_engine.dto.MemoryPolicyResult;
import dev.automata.automata.dto.ConditionMemory;
import org.springframework.stereotype.Component;

@Component
public class EdgeFallingMemoryPolicy implements IMemoryPolicyStrategy {
    public ConditionMemoryPolicy.MemoryType type() {
        return ConditionMemoryPolicy.MemoryType.EDGE_FALLING;
    }

    public MemoryPolicyResult apply(ConditionMemoryPolicy policy, boolean rawResult, ConditionMemory memory, long nowMs) {
        Boolean prev = memory.getPreviousRawResult();
        boolean edge = !rawResult && (prev != null && prev);
        ConditionMemory updated = (rawResult ? memory.withRawTrue(nowMs) : memory.withRawFalse())
                .withPolicyPassed(edge);
        return new MemoryPolicyResult(edge, updated,
                edge ? "EDGE_FALLING: fired" : "EDGE_FALLING: no edge (raw=" + rawResult + ")");
    }

    public String summarize(ConditionMemoryPolicy policy, ConditionMemory memory) {
        return "EDGE_FALLING: prev=" + memory.getPreviousRawResult();
    }
}
