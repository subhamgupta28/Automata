package dev.automata.automata.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AutomationRuntimeState {

    // ── Optimistic concurrency ─────────────────────────────────────────────
    private long version = 0;

    // ── Top-level state ────────────────────────────────────────────────────
    private String topLevelState = "IDLE";

    /**
     * Unified per-node state map. Replaces both the old branchStates and
     * nodeActiveStates maps. Every condition node that has negative actions
     * (stateful=true) gets tracked here.
     * nodeId → "IDLE" | "ACTIVE" | "HOLDING"
     */
    private Map<String, String> nodeStates = new HashMap<>();

    /**
     * @deprecated kept only for backward-compat deserialization of old Redis
     * blobs. New code must use nodeStates. Reads fall back to this map if
     * nodeStates does not contain the key.
     */
    @Deprecated
    private Map<String, String> branchStates = new HashMap<>();

    /**
     * @deprecated kept only for backward-compat deserialization of old Redis
     * blobs. New code must use nodeStates.
     */
    @Deprecated
    private Map<String, Boolean> nodeActiveStates = new HashMap<>();

    // ── Condition memory (DURATION / CONSECUTIVE / EDGE policies) ─────────
    private Map<String, ConditionMemory> conditionMemories = new HashMap<>();

    // ── Coalition trigger tracking ─────────────────────────────────────────
    private Map<String, Long> triggerMemberLastFired = new HashMap<>();
    private int sequenceProgress = 0;

    // ── Eval snapshot (written post-CAS, not version-guarded) ─────────────
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
        next.nodeStates = new HashMap<>(this.nodeStates);
        // carry deprecated maps for blobs that may still have them
        next.branchStates = new HashMap<>(this.branchStates);
        next.nodeActiveStates = new HashMap<>(this.nodeActiveStates);
        next.conditionMemories = new HashMap<>(this.conditionMemories);
        next.triggerMemberLastFired = new HashMap<>(this.triggerMemberLastFired);
        next.sequenceProgress = this.sequenceProgress;
        next.lastEvalSnapshot = this.lastEvalSnapshot;
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
    // UNIFIED NODE STATE  (replaces branchStates + nodeActiveStates)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Returns true if the node is currently ACTIVE or HOLDING.
     * Falls back to the deprecated maps for old Redis blobs that haven't
     * been rewritten yet.
     */
    public boolean isNodeActive(String nodeId) {
        String s = nodeStates.get(nodeId);
        if (s != null) return "ACTIVE".equals(s) || "HOLDING".equals(s);
        // legacy fallback — branchStates used by old branch automations
        s = branchStates.get(nodeId);
        if (s != null) return "ACTIVE".equals(s) || "HOLDING".equals(s);
        // legacy fallback — nodeActiveStates used by old tree nodes
        return Boolean.TRUE.equals(nodeActiveStates.get(nodeId));
    }

    public String getNodeStateStr(String nodeId) {
        String s = nodeStates.get(nodeId);
        if (s != null) return s;
        s = branchStates.get(nodeId);
        if (s != null) return s;
        return "IDLE";
    }

    public void setNodeState(String nodeId, String state) {
        nodeStates.put(nodeId, state);
    }

    /**
     * Sets every node in nodeStates to IDLE in one call.
     */
    public void resetAllNodeStates() {
        nodeStates.replaceAll((k, v) -> "IDLE");
        // also clear deprecated maps so old blobs don't leave stale state
        branchStates.replaceAll((k, v) -> "IDLE");
        nodeActiveStates.replaceAll((k, v) -> false);
    }


    // ─────────────────────────────────────────────────────────────────────
    // CONDITION MEMORY
    // ─────────────────────────────────────────────────────────────────────

    public ConditionMemory getConditionMemory(String nodeId) {
        return conditionMemories.getOrDefault(nodeId, ConditionMemory.fresh());
    }

    public void setConditionMemory(String nodeId, ConditionMemory memory) {
        conditionMemories.put(nodeId, memory);
    }


    // ─────────────────────────────────────────────────────────────────────
    // COALITION TRACKING
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
    // EVAL SNAPSHOT
    // ─────────────────────────────────────────────────────────────────────

    @Data
    @NoArgsConstructor
    public static class EvalSnapshot {
        private String outcome;
        private String traceId;
        private Date evaluatedAt;
        private boolean c1True;
        private boolean anyWasActive;
        private String reason;
        private Long evalDurationMs;

        /**
         * nodeId → true/false raw result of each condition node last tick.
         */
        private Map<String, Boolean> conditionResults;

        /**
         * nodeId → human-readable memory policy summary.
         */
        private Map<String, String> conditionMemorySummaries;

        /**
         * nodeId → state string after last eval (replaces old branchStates field).
         */
        private Map<String, String> nodeStates;

        /**
         * deviceId → epochMs of last coalition member fire.
         */
        private Map<String, Long> coalitionLastFired;

        private int sequenceProgress;
    }
}