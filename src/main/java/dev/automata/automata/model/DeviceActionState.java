package dev.automata.automata.model;


import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.Instant;
import java.util.Date;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class DeviceActionState {

    @Id
    private String id;
    private String deviceId;
    private Map<String, Object> payload;
    private String deviceType;
    private String user;
    private Date timestamp;
    private Map<String, Object> deviceCurrentState;

    // ── Identity ──────────────────────────────────────────────────────────
    @Indexed
    private String automationId;
    private String automationName;

    @Indexed
    private String traceId;          // links back to AutomationLog entry
    private String correlationId;    // _cid sent to device; used by ActionDeliveryTracker for ACK matching

    // ── Device ────────────────────────────────────────────────────────────
    private String deviceName;       // action.getName() — human label

    // ── Action ────────────────────────────────────────────────────────────
    private String key;              // action.getKey()  e.g. "bright", "preset", "channel1"
    private String data;             // action.getData() e.g. "180", "true"
    private int order;            // action.getOrder()
    private int delaySeconds;     // action.getDelaySeconds()
    private String conditionGroup;   // "positive" or "negative" — from compiled action (add to CompiledAction)

    // ── Timing ────────────────────────────────────────────────────────────
    private Date dispatchedAt;     // when sendToDevice() was called
    private Date ackedAt;          // filled by ActionDeliveryTracker on device ACK; null = pending

    // ── Outcome ───────────────────────────────────────────────────────────
    private DispatchOutcome outcome; // see enum below
    private String errorReason;      // non-null only when outcome = DISPATCH_FAILED

    // ── TTL support ───────────────────────────────────────────────────────
    // MongoDB TTL index on this field: db.device_action_states.createIndex({expireAt:1},{expireAfterSeconds:0})
    @Indexed
    private Date expireAt;           // set to dispatchedAt + 30 days by default

    public enum DispatchOutcome {
        PENDING,           // sent, awaiting device ACK
        ACKED,             // device confirmed receipt via _cid correlation
        DELIVERY_FAILED,   // dispatcher threw, or ACK timed out
        NOT_APPLICABLE     // no ACK path (alert, app_notify, WLED, MEDIA)
    }

    @Indexed
    @LastModifiedDate
    private Instant updateDate;
}
