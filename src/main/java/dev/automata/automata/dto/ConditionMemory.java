package dev.automata.automata.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Per-node runtime memory persisted inside AutomationRuntimeState.conditionMemories.
 * <p>
 * Keyed by nodeId. Serialised to/from the same Redis JSON blob as the rest of
 * AutomationRuntimeState so it benefits from the same CAS write and TTL.
 * <p>
 * Fields
 * ──────
 * firstTrueEpochMs   — wall-clock ms when the condition first became true in the
 * current continuous run. Reset to 0 when the condition is false.
 * Used by DURATION policy.
 * <p>
 * consecutiveTrueCount — how many consecutive evaluation cycles the condition has
 * been true.  Reset to 0 on false.
 * Used by CONSECUTIVE_TICKS policy.
 * <p>
 * previousRawResult  — the raw evalSingleCondition result from the previous tick,
 * BEFORE the memory policy was applied.
 * Used by EDGE_RISING / EDGE_FALLING / EDGE_BOTH policies.
 * <p>
 * policyPassed       — the resolved result AFTER the memory policy was applied on
 * the last tick.  Stored so that external inspection (Point 9)
 * can show "raw=true but policy not yet satisfied".
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConditionMemory {

    /**
     * Epoch-ms when this condition first became continuously true. 0 = not active.
     */
    private long firstTrueEpochMs;

    /**
     * Number of consecutive true evaluations (resets on false).
     */
    private int consecutiveTrueCount;

    /**
     * Raw evalSingleCondition result from the previous evaluation tick.
     * Null means this is the very first evaluation (no prior state).
     */
    private Boolean previousRawResult;

    /**
     * The result after the memory policy was applied on the most recent tick.
     * Stored for introspection; not used by the evaluator directly (it recomputes).
     */
    private Boolean policyPassed;

    // ── Factories ─────────────────────────────────────────────────────────

    public static ConditionMemory fresh() {
        return ConditionMemory.builder()
                .firstTrueEpochMs(0L)
                .consecutiveTrueCount(0)
                .previousRawResult(null)
                .policyPassed(null)
                .build();
    }

    // ── Mutation helpers (called by AutomationEvaluator, returns new instance) ──

    public ConditionMemory withRawTrue(long nowMs) {
        return ConditionMemory.builder()
                .firstTrueEpochMs(firstTrueEpochMs == 0 ? nowMs : firstTrueEpochMs)
                .consecutiveTrueCount(consecutiveTrueCount + 1)
                .previousRawResult(true)
                .policyPassed(policyPassed)   // updated separately
                .build();
    }

    public ConditionMemory withRawFalse() {
        return ConditionMemory.builder()
                .firstTrueEpochMs(0L)
                .consecutiveTrueCount(0)
                .previousRawResult(false)
                .policyPassed(false)
                .build();
    }

    public ConditionMemory withPolicyPassed(boolean passed) {
        return ConditionMemory.builder()
                .firstTrueEpochMs(firstTrueEpochMs)
                .consecutiveTrueCount(consecutiveTrueCount)
                .previousRawResult(previousRawResult)
                .policyPassed(passed)
                .build();
    }
}