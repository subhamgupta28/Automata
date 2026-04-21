package dev.automata.automata.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;
import java.util.Map;

@lombok.Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "automation_logs")
@CompoundIndexes({
        @CompoundIndex(name = "automation_timestamp_idx", def = "{'automationId': 1, 'timestamp': -1}"),
        @CompoundIndex(name = "timestamp_status_idx", def = "{'timestamp': -1, 'status': 1}")
})
public class AutomationLog {

    @Id
    private String id;

    @Indexed
    private String automationId;
    private String automationName;
    private String user;

    private LogStatus status; // TRIGGERED, SKIPPED, RESTORED, ERROR

    private String reason;                  // Human-readable why it ran or didn't
    private List<ConditionResult> conditionResults; // Per-condition evaluation detail
    private String operatorLogic;           // "AND" / "OR"
    private Map<String, Object> payload;    // The sensor data at time of evaluation
    private Map<String, Boolean> conditionOutcomes;

    private String triggerType;             // "time", "state", "periodic"
    private String triggerDeviceId;
    private String snoozeState;
    @Indexed
    private Date timestamp;
    private Date endTimestamp;

// ─────────────────────────────────────────────────────────────────────
    // STATUS
    // ─────────────────────────────────────────────────────────────────────

    public enum LogStatus {
        /**
         * Trigger condition false — informational/negative actions fired, no state change.
         */
        TRIGGER_FALSE,

        /**
         * Gate condition true, positive actions fired, branch entered ACTIVE.
         */
        TRIGGERED,

        /**
         * Gate condition false (was ACTIVE), negative actions fired, branch returned IDLE.
         */
        RESTORED,

        /**
         * Gate condition true but outprioritised by a higher-priority branch.
         * No actions fired, no state change for this branch.
         */
        SUPPRESSED,

        /**
         * Manually triggered by user.
         */
        USER_OVERRIDE,

        /**
         * Condition not met — quiet path, no actions.
         */
        NOT_MET,

        /**
         * Skipped due to lock, snooze, timed-disable, or still active.
         */
        SKIPPED,

        /**
         * An exception occurred during execution.
         */
        ERROR
    }

    @lombok.Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConditionResult {
        private String triggerKey;
        private String conditionType;   // "above", "below", "range", "scheduled", "equal"
        private String actualValue;
        private String expectedValue;   // threshold / time / range
        private boolean passed;
        private String detail;          // e.g. "200.0 > 195.0 (with buffer 5.0)"
        private String conditionNodeId;
        private List<String> days;
        private boolean isGateCondition;
        /**
         * For gate conditions: which branch/operator this gate belongs to.
         */
        private String operatorNodeId;

        /**
         * If this branch was suppressed by a higher-priority branch,
         * records the winning operator nodeId.
         */
        private String suppressedByOperatorNodeId;
    }
}
