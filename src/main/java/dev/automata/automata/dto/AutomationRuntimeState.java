package dev.automata.automata.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Mutable runtime state for one automation, stored as a JSON blob in Redis.
 * <p>
 * Changes vs previous version
 * ───────────────────────────
 * 1. conditionMemories  — Map<nodeId, ConditionMemory> for Point 2 (condition memory).
 * The evaluator reads and updates this map via applyMemoryPolicy().
 * Serialised alongside the existing fields in the same CAS-protected blob.
 * <p>
 * 2. triggerMemberLastFired — Map<deviceId, epochMs> for Point 1 (coalition).
 * AutomationOrchestrator.recordTriggerFired() writes this before evaluation.
 * CoalitionGuard reads it to check quorum/sequence/veto windows.
 * <p>
 * 3. sequenceProgress — int, 0-based index of the next expected member in a
 * SEQUENCE coalition.  Reset to 0 when the sequence times out or completes.
 * <p>
 * 4. lastEvalSnapshot — snapshot of the most recent EvalResult for Point 9
 * (state inspection API). Written post-CAS so it doesn't affect CAS itself.
 * Stored as a nested object rather than a separate Redis key so the state
 * inspection endpoint needs only one Redis GET.
 * <p>
 * All existing fields (version, topLevelState, branchStates, nodeActiveStates,
 * lastExecutionTime) are preserved unchanged.
 */
@Data
@NoArgsConstructor
public class AutomationRuntimeState {

    // ── Optimistic concurrency ─────────────────────────────────────────────
    private long version = 0;

    // ── Top-level state (no-branch automations) ────────────────────────────
    private String topLevelState = "IDLE";   // "IDLE" | "ACTIVE"

    // ── Branch states (branch automations) ────────────────────────────────
    private Map<String, String> branchStates = new HashMap<>();  // nodeId → "IDLE"|"ACTIVE"|"HOLDING"

    // ── Per-node active flags (condition tree hysteresis) ─────────────────
    private Map<String, Boolean> nodeActiveStates = new HashMap<>();  // nodeId → true/false

    // ── Point 2: condition memory (DURATION / CONSECUTIVE / EDGE policies) ─
    private Map<String, ConditionMemory> conditionMemories = new HashMap<>();  // nodeId → memory

    // ── Point 1: coalition trigger tracking ───────────────────────────────
    /**
     * Last epoch-ms each coalition member fired. deviceId → epochMs.
     */
    private Map<String, Long> triggerMemberLastFired = new HashMap<>();

    /**
     * For SEQUENCE coalitions: 0-based index of the NEXT expected member.
     * 0 means waiting for the first member. Resets to 0 on timeout or completion.
     */
    private int sequenceProgress = 0;

    // ── Point 9: last eval snapshot (written post-CAS, not versioned) ─────
    private EvalSnapshot lastEvalSnapshot;

    // ── Bookkeeping ───────────────────────────────────────────────────────
    private Date lastExecutionTime;

    // ─────────────────────────────────────────────────────────────────────
    // FACTORY
    // ─────────────────────────────────────────────────────────────────────

    public static AutomationRuntimeState idle() {
        return new AutomationRuntimeState();
    }

    // ─────────────────────────────────────────────────────────────────────
    // VERSION
    // ─────────────────────────────────────────────────────────────────────

    public AutomationRuntimeState withNextVersion() {
        AutomationRuntimeState next = new AutomationRuntimeState();
        next.version = this.version + 1;
        next.topLevelState = this.topLevelState;
        next.branchStates = new HashMap<>(this.branchStates);
        next.nodeActiveStates = new HashMap<>(this.nodeActiveStates);
        next.conditionMemories = new HashMap<>(this.conditionMemories);
        next.triggerMemberLastFired = new HashMap<>(this.triggerMemberLastFired);
        next.sequenceProgress = this.sequenceProgress;
        next.lastEvalSnapshot = this.lastEvalSnapshot;   // carried forward; overwritten post-CAS
        next.lastExecutionTime = this.lastExecutionTime;
        return next;
    }

    // ─────────────────────────────────────────────────────────────────────
    // TOP-LEVEL STATE
    // ─────────────────────────────────────────────────────────────────────

    public boolean isTopLevelActive() {
        return "ACTIVE".equals(topLevelState);
    }

    // ─────────────────────────────────────────────────────────────────────
    // BRANCH STATE
    // ─────────────────────────────────────────────────────────────────────

    public boolean isBranchActive(String gateNodeId) {
        String s = branchStates.get(gateNodeId);
        return "ACTIVE".equals(s) || "HOLDING".equals(s);
    }

    public String getBranchStateStr(String gateNodeId) {
        return branchStates.getOrDefault(gateNodeId, "IDLE");
    }

    public void setBranchState(String gateNodeId, String state) {
        branchStates.put(gateNodeId, state);
    }

    // ─────────────────────────────────────────────────────────────────────
    // PER-NODE ACTIVE FLAGS
    // ─────────────────────────────────────────────────────────────────────

    public boolean isNodeActive(String nodeId) {
        return Boolean.TRUE.equals(nodeActiveStates.get(nodeId));
    }

    public void setNodeActive(String nodeId, boolean active) {
        nodeActiveStates.put(nodeId, active);
    }

    // ─────────────────────────────────────────────────────────────────────
    // CONDITION MEMORY  (Point 2)
    // ─────────────────────────────────────────────────────────────────────

    public ConditionMemory getConditionMemory(String nodeId) {
        return conditionMemories.getOrDefault(nodeId, ConditionMemory.fresh());
    }

    public void setConditionMemory(String nodeId, ConditionMemory memory) {
        conditionMemories.put(nodeId, memory);
    }

    // ─────────────────────────────────────────────────────────────────────
    // COALITION TRIGGER TRACKING  (Point 1)
    // ─────────────────────────────────────────────────────────────────────

    public void recordMemberFired(String deviceId, long epochMs) {
        triggerMemberLastFired.put(deviceId, epochMs);
    }

    public long getMemberLastFired(String deviceId) {
        return triggerMemberLastFired.getOrDefault(deviceId, 0L);
    }

    public void resetCoalitionState() {
        triggerMemberLastFired.clear();
        sequenceProgress = 0;
    }

    // ─────────────────────────────────────────────────────────────────────
    // EVAL SNAPSHOT  (Point 9)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Lightweight snapshot of the most recent evaluation result.
     * Written by the orchestrator post-CAS via stateStore.writeEvalSnapshot().
     * Not included in the CAS version comparison — only the fields above are
     * version-guarded. The snapshot is best-effort: if two concurrent executions
     * race, whichever writes last wins (acceptable for diagnostic purposes).
     */
    @Data
    @NoArgsConstructor
    public static class EvalSnapshot {
        private String outcome;          // EvalOutcome.name()
        private String traceId;
        private Date evaluatedAt;
        private boolean c1True;
        private boolean anyWasActive;
        private String reason;
        private Long evalDurationMs;

        /**
         * nodeId → true/false — raw result of each condition node last tick.
         */
        private Map<String, Boolean> conditionResults;

        /**
         * nodeId → human-readable summary of memory policy state last tick.
         * e.g. "DURATION: 45/120s", "EDGE_RISING: fired", "CONSECUTIVE: 2/3"
         */
        private Map<String, String> conditionMemorySummaries;

        /**
         * gateNodeId → branch state string after last eval.
         */
        private Map<String, String> branchStates;

        /**
         * deviceId → epochMs of last coalition member fire.
         */
        private Map<String, Long> coalitionLastFired;

        /**
         * For SEQUENCE: index of the next expected member at last eval.
         */
        private int sequenceProgress;
    }
}