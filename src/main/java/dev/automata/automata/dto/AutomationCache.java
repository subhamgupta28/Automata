package dev.automata.automata.dto;


import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.automata.automata.model.Automation;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class AutomationCache {
    private String id;

    @JsonProperty("automation")
    private Automation automation;
    private String triggerDeviceId;
    private String triggerDeviceType;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    private Date lastUpdate;
    private Date previousExecutionTime;
    private boolean triggeredPreviously;
    private Boolean isActive;
    private boolean enabled;
    private Map<String, Object> previousState; // deviceId -> state
    private boolean active; // already exists but use properly

    private Date lastExecutionTime;
    private Date lastStateChangeTime;

    private Long conditionFirstTrueAt;
    /**
     * Top-level state — ACTIVE if ANY branch is active, IDLE otherwise.
     * Kept for backward-compatible reads; the source of truth for per-branch
     * logic is {@link #branchStates}.
     */
    @Builder.Default
    private AutomationState state = AutomationState.IDLE;

    private Map<String, AutomationState> branchStates = new HashMap<>();
// key = gate condition nodeId → IDLE / ACTIVE / HOLDING
    // ── Branch-state helpers ──────────────────────────────────────────────

    public AutomationState getBranchState(String gateNodeId) {
        return branchStates.getOrDefault(gateNodeId, AutomationState.IDLE);
    }

    public void setBranchState(String gateNodeId, AutomationState state) {
        branchStates.put(gateNodeId, state);
        // Sync top-level state: ACTIVE if any branch is non-IDLE
        this.state = branchStates.values().stream()
                .anyMatch(s -> s != AutomationState.IDLE)
                ? AutomationState.ACTIVE
                : AutomationState.IDLE;
    }

    @JsonIgnore
    public boolean isAnyBranchActive() {
        return branchStates.values().stream()
                .anyMatch(s -> s == AutomationState.ACTIVE || s == AutomationState.HOLDING);
    }
}
