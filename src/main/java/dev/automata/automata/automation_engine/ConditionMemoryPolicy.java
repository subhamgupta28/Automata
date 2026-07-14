package dev.automata.automata.automation_engine;

import lombok.Builder;
import lombok.Value;

/**
 * Optional memory policy attached to a CompiledConditionNode.
 * When present, the evaluator maintains per-node memory in
 * AutomationRuntimeState.conditionMemories and applies the policy
 * before returning the raw boolean result of evalSingleCondition.
 * <p>
 * Types:
 * <p>
 * DURATION
 * The condition must evaluate true continuously for at least
 * requiredDurationSeconds before this node is considered "passed".
 * The first-true timestamp is recorded; on each subsequent true tick
 * the elapsed time is checked. A false tick resets the timer.
 * Useful for: "charge only after battery has been below 30% for 2+ minutes"
 * (prevents reacting to brief sensor noise)
 * <p>
 * CONSECUTIVE_TICKS
 * The condition must evaluate true for at least requiredTicks consecutive
 * evaluation cycles. A false tick resets the counter.
 * Useful for: "alert only after 3 consecutive readings above threshold"
 * <p>
 * EDGE_RISING
 * True ONLY on the tick where the condition transitions from false → true.
 * Subsequent ticks where it remains true produce false (already crossed).
 * Useful for: "fire once when motion is first detected"
 * "fire once when battery first drops below threshold"
 * <p>
 * EDGE_FALLING
 * True ONLY on the tick where the condition transitions from true → false.
 * Useful for: "notify the moment a device goes offline"
 * <p>
 * EDGE_BOTH
 * True on any transition (false→true or true→false).
 * Useful for: "log every state change"
 */
@Value
@Builder
public class ConditionMemoryPolicy {

    public enum MemoryType {
        DURATION,
        CONSECUTIVE_TICKS,
        EDGE_RISING,
        EDGE_FALLING,
        EDGE_BOTH
    }

    MemoryType type;

    /**
     * DURATION: minimum seconds the condition must be continuously true.
     */
    int requiredDurationSeconds;

    /**
     * CONSECUTIVE_TICKS: minimum number of consecutive true evaluations.
     */
    int requiredTicks;
}