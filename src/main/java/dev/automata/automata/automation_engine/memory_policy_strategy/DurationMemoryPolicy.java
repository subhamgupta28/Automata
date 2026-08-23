package dev.automata.automata.automation_engine.memory_policy_strategy;

import dev.automata.automata.automation_engine.ConditionMemoryPolicy;
import dev.automata.automata.automation_engine.dto.MemoryPolicyResult;
import dev.automata.automata.dto.ConditionMemory;
import org.springframework.stereotype.Component;

@Component
public class DurationMemoryPolicy implements IMemoryPolicyStrategy {
    public ConditionMemoryPolicy.MemoryType type() {
        return ConditionMemoryPolicy.MemoryType.DURATION;
    }

    public MemoryPolicyResult apply(ConditionMemoryPolicy policy, boolean rawResult, ConditionMemory memory, long nowMs) {
        if (!rawResult) return new MemoryPolicyResult(false, memory.withRawFalse(), "DURATION: reset (false)");
        long firstTrue = memory.getFirstTrueEpochMs() > 0 ? memory.getFirstTrueEpochMs() : nowMs;
        var updated = memory.withRawTrue(firstTrue).withPolicyPassed(false);
        long elapsedSec = (nowMs - firstTrue) / 1000;
        boolean passes = elapsedSec >= policy.getRequiredDurationSeconds();
        updated = updated.withPolicyPassed(passes);
        return new MemoryPolicyResult(passes, updated, "DURATION: " + elapsedSec + "/" + policy.getRequiredDurationSeconds() + "s");
    }

    public String summarize(ConditionMemoryPolicy policy, ConditionMemory memory) {
        long elapsed = memory.getFirstTrueEpochMs() > 0
                ? (System.currentTimeMillis() - memory.getFirstTrueEpochMs()) / 1000 : 0;
        return "DURATION: " + elapsed + "/" + policy.getRequiredDurationSeconds() + "s";
    }
}

