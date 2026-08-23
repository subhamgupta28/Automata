package dev.automata.automata.automation_engine.memory_policy_strategy;

import dev.automata.automata.automation_engine.ConditionMemoryPolicy;
import dev.automata.automata.automation_engine.dto.MemoryPolicyResult;
import dev.automata.automata.dto.ConditionMemory;
import org.springframework.stereotype.Component;

@Component
public class EdgeBothMemoryPolicy implements IMemoryPolicyStrategy {
    public ConditionMemoryPolicy.MemoryType type() {
        return ConditionMemoryPolicy.MemoryType.EDGE_BOTH;
    }

    public MemoryPolicyResult apply(ConditionMemoryPolicy policy, boolean rawResult, ConditionMemory memory, long nowMs) {
        Boolean prev = memory.getPreviousRawResult();
        boolean edge = prev == null ? rawResult : (rawResult != prev);
        ConditionMemory updated = (rawResult ? memory.withRawTrue(nowMs) : memory.withRawFalse())
                .withPolicyPassed(edge);
        return new MemoryPolicyResult(edge, updated,
                edge ? "EDGE_BOTH: fired (" + prev + "→" + rawResult + ")" : "EDGE_BOTH: no edge");
    }

    public String summarize(ConditionMemoryPolicy policy, ConditionMemory memory) {
        return "EDGE_BOTH: prev=" + memory.getPreviousRawResult();
    }
}
