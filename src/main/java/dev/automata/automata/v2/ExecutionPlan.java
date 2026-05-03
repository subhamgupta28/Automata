package dev.automata.automata.v2;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("automation_plans")
public class ExecutionPlan {

    @Id
    private String automationId;

    private String automationName;
    @Indexed
    private String triggerDeviceId;

    /**
     * Bump this constant whenever compilation logic changes.
     * Startup reconciliation skips plans with schemaVersion < CURRENT and
     * logs a warning to re-save those automations.
     */
    public static final int CURRENT_SCHEMA_VERSION = 2;
    private int schemaVersion;
    private Date compiledAt;

    // ── Trigger-side conditions, topologically sorted (root first, chained after) ──
    private List<CompiledCondition> triggerConditions;

    // ── Gate branches sorted by operator priority DESC ────────────────────
    // Empty/null = no-branch automation → uses topLevelPositiveActions/topLevelNegativeActions
    private List<CompiledBranch> branches;

    // ── Top-level positive/negative for no-branch automations ────────────
    // Bug fix: previously these actions had no compiled slot and were silently
    // lost at eval time.  These are conditionGroup="positive"/"negative" actions
    // that reference trigger condition nodes (no operator/gate in between).
    private List<CompiledAction> topLevelPositiveActions;
    private List<CompiledAction> topLevelNegativeActions;

    // ── Global action groups ───────────────────────────────────────────────
    /**
     * conditionGroup="informational" — fire only when c1 false AND a branch was active
     */
    private List<CompiledAction> informationalActions;

    /**
     * conditionGroup="fallback" — fire when c1 true but no gate branch matched
     */
    private List<CompiledAction> fallbackActions;

    /**
     * conditionGroup="none" — stateless, fires whenever referenced node is true
     */
    private List<CompiledAction> statelessActions;

    /**
     * conditionGroup="negative" referencing trigger condition nodes.
     * Fires when c1 turns false and at least one branch was ACTIVE.
     */
    private List<CompiledAction> c1NegativeActions;


    // ─────────────────────────────────────────────────────────────────────
    // Nested types
    // ─────────────────────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompiledCondition {
        private String nodeId;
        private String conditionType;  // "range"|"above"|"below"|"equal"|"scheduled"
        private String triggerKey;
        private String deviceId;       // null = primary device payload

        private String value;
        private String above;
        private String below;
        private boolean isExact;

        private String scheduleType;   // "range"|"interval"|"solar"|"at"
        private String fromTime;
        private String toTime;
        private String time;
        private List<String> days;
        private String solarType;
        private int offsetMinutes;
        private int intervalMinutes;
        private int durationMinutes;

        /**
         * For chained conditions: nodeId of parent condition. null for root triggers.
         */
        private String parentConditionNodeId;
        private boolean isChained;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompiledBranch {
        private String gateNodeId;
        private int priority;
        private CompiledCondition gateCondition;
        /**
         * Pre-sorted by action order ASC
         */
        private List<CompiledAction> positiveActions;
        /**
         * Pre-sorted by action order ASC. Deduplicated by (deviceId,key,data).
         */
        private List<CompiledAction> negativeActions;

        public boolean hasDuration() {
            return gateCondition != null && gateCondition.getDurationMinutes() > 0;
        }

        public boolean hasNegativeActions() {
            return negativeActions != null && !negativeActions.isEmpty();
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompiledAction {
        private String nodeId;
        private String deviceId;
        private String key;
        private String data;
        private int order;
        private int delaySeconds;
        private String name;
        private String deviceType; // cached at compile time — avoids DB lookup per action
    }


    // ─────────────────────────────────────────────────────────────────────
    // Convenience
    // ─────────────────────────────────────────────────────────────────────

    public boolean hasBranches() {
        return branches != null && !branches.isEmpty();
    }

    public boolean hasDataDrivenTrigger() {
        if (triggerConditions == null) return false;
        return triggerConditions.stream()
                .anyMatch(c -> !"scheduled".equals(c.getConditionType()));
    }

    public boolean hasTopLevelPositiveActions() {
        return topLevelPositiveActions != null && !topLevelPositiveActions.isEmpty();
    }

    public boolean hasTopLevelNegativeActions() {
        return topLevelNegativeActions != null && !topLevelNegativeActions.isEmpty();
    }
}