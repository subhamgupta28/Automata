package dev.automata.automata.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Lean runtime state stored under AUTOMATION_STATE:<automationId>.
 * <p>
 * Deliberately does NOT contain the Automation graph (conditions, actions,
 * operators). The graph lives in AUTOMATION_DEF:<automationId> and is
 * invalidated only when the automation is saved.
 * <p>
 * version: monotonic counter used for optimistic CAS writes. Every successful
 * state write increments it. A write that finds a stale version fails and the
 * caller retries after re-reading.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AutomationRuntimeState {

    /**
     * Matches AutomationState enum (IDLE / ACTIVE / HOLDING).
     */
    @Builder.Default
    private String topLevelState = "IDLE";

    /**
     * Per-branch state keyed by gate condition nodeId.
     * Value is one of "IDLE" / "ACTIVE" / "HOLDING".
     */
    @Builder.Default
    private Map<String, String> branchStates = new HashMap<>();

    /**
     * When the last positive action sequence started.
     */
    private Date lastExecutionTime;

    /**
     * Monotonic version — incremented atomically on every successful write.
     */
    @Builder.Default
    private long version = 0L;

    /**
     * Last time this record was written (for TTL-refresh decisions).
     */
    private Date lastUpdated;

    // ── Convenience accessors ─────────────────────────────────────────────

    public boolean isBranchActive(String gateNodeId) {
        String s = branchStates.getOrDefault(gateNodeId, "IDLE");
        return "ACTIVE".equals(s) || "HOLDING".equals(s);
    }

    public boolean isBranchHolding(String gateNodeId) {
        return "HOLDING".equals(branchStates.getOrDefault(gateNodeId, "IDLE"));
    }

    public void setBranchState(String gateNodeId, String state) {
        branchStates.put(gateNodeId, state);
    }

    public String getBranchStateStr(String gateNodeId) {
        return branchStates.getOrDefault(gateNodeId, "IDLE");
    }

    public boolean isTopLevelActive() {
        return "ACTIVE".equals(topLevelState) || "HOLDING".equals(topLevelState);
    }

    /**
     * Returns a deep copy with version incremented — used before a CAS write.
     */
    public AutomationRuntimeState withNextVersion() {
        return AutomationRuntimeState.builder()
                .topLevelState(this.topLevelState)
                .branchStates(new HashMap<>(this.branchStates))
                .lastExecutionTime(this.lastExecutionTime)
                .version(this.version + 1)
                .lastUpdated(new Date())
                .build();
    }

    /**
     * Factory — fresh IDLE state for an automation that has never run.
     */
    public static AutomationRuntimeState idle() {
        return AutomationRuntimeState.builder()
                .topLevelState("IDLE")
                .branchStates(new HashMap<>())
                .version(0L)
                .lastUpdated(new Date())
                .build();
    }
}
