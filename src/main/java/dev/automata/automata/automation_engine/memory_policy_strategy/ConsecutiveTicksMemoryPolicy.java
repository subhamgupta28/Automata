package dev.automata.automata.automation_engine.memory_policy_strategy;

import dev.automata.automata.automation_engine.ConditionMemoryPolicy;
import dev.automata.automata.automation_engine.dto.MemoryPolicyResult;
import dev.automata.automata.dto.ConditionMemory;
import org.springframework.stereotype.Component;

@Component
public class ConsecutiveTicksMemoryPolicy implements IMemoryPolicyStrategy {
    public ConditionMemoryPolicy.MemoryType type() {
        return ConditionMemoryPolicy.MemoryType.CONSECUTIVE_TICKS;
    }

    public MemoryPolicyResult apply(ConditionMemoryPolicy policy, boolean rawResult, ConditionMemory memory, long nowMs) {
        if (!rawResult) {
            return new MemoryPolicyResult(false, memory.withRawFalse(), "CONSECUTIVE: reset (false)");
        }
        ConditionMemory updated = memory.withRawTrue(nowMs);
        int count = updated.getConsecutiveTrueCount();
        boolean passes = count >= policy.getRequiredTicks();
        updated = updated.withPolicyPassed(passes);
        return new MemoryPolicyResult(passes, updated,
                "CONSECUTIVE: " + count + "/" + policy.getRequiredTicks());
    }

    public String summarize(ConditionMemoryPolicy policy, ConditionMemory memory) {
        return "CONSECUTIVE: " + memory.getConsecutiveTrueCount() + "/" + policy.getRequiredTicks();
    }
}
