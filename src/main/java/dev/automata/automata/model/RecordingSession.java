package dev.automata.automata.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;

@lombok.Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "recording_sessions")
public class RecordingSession {

    @Id
    private String id;

    private String userId;

    /**
     * Human-readable label, e.g. "GPS Drive – June 12"
     */
    private String name;

    /**
     * Which devices to record. If empty, records ALL devices that publish during the session.
     */
    private List<String> deviceIds;

    private TriggerType triggerType;   // MANUAL | CONDITION | AUTOMATION

    /**
     * Only set when triggerType == CONDITION
     */
    private RecordingCondition startCondition;

    /**
     * Optional: auto-stop when this condition becomes true
     */
    private RecordingCondition stopCondition;

    /**
     * Auto-stop after this many seconds (0 = no limit)
     */
    private long durationLimitSecs;

    private SessionStatus status;      // PENDING | ACTIVE | STOPPED | ERROR

    @Indexed
    private Date startTime;
    private Date endTime;

    /**
     * Total flushed reading count across all devices
     */
    private long recordCount;

    /**
     * Set when started via an Automation action
     */
    private String sourceAutomationId;

    private Date createdAt;
    private Date updatedAt;

    public enum TriggerType {
        MANUAL, CONDITION, AUTOMATION
    }

    public enum SessionStatus {
        PENDING, ACTIVE, STOPPED, ERROR
    }

    @lombok.Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecordingCondition {
        private String deviceId;
        private String field;
        private String operator;   // GT, LT, EQ, GTE, LTE, NEQ
        private String value;
    }
}