package dev.automata.automata.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;

import java.util.Date;
import java.util.List;
import java.util.Map;

@lombok.Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "automation_logs")
public class AutomationLog {

    @Id
    private String id;

    private String automationId;
    private String automationName;

    private LogStatus status; // TRIGGERED, SKIPPED, RESTORED, ERROR

    private String reason;                  // Human-readable why it ran or didn't
    private List<ConditionResult> conditionResults; // Per-condition evaluation detail
    private String operatorLogic;           // "AND" / "OR"
    private Map<String, Object> payload;    // The sensor data at time of evaluation

    private String triggerType;             // "time", "state", "periodic"
    private String triggerDeviceId;

    @Indexed
    private Date timestamp;

    public enum LogStatus {
        TRIGGERED,   // Conditions met, actions executed
        SKIPPED,     // Already triggered previously, cooldown active
        RESTORED,    // Conditions cleared, state reverted
        NOT_MET,     // Conditions evaluated but not satisfied
        ERROR        // Exception during evaluation
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
    }
}
