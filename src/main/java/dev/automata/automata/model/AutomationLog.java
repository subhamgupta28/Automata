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

    public enum LogStatus {
        TRIGGERED,   // Conditions met, actions executed
        SKIPPED,     // Already triggered previously, cooldown active
        RESTORED,    // Conditions cleared, state reverted
        NOT_MET,     // Conditions evaluated but not satisfied
        ERROR,        // Exception during evaluation
        USER_OVERRIDE
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
    }
}
