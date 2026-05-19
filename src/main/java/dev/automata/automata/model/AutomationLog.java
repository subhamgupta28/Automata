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

/**
 * Persisted log entry for one automation evaluation that resulted in a
 * state change (TRIGGERED, RESTORED, TRIGGER_FALSE, etc.).
 *
 * Design constraints:
 *   - Written ONLY by AutomationLogStream.flush() — never by AutomationService
 *     directly. The log stream batches writes and flushes every 5 seconds so
 *     high-frequency evaluation ticks (12s intervals × many automations) do
 *     not cause a MongoDB write storm.
 *   - SKIPPED and NOT_MET outcomes are NOT persisted (too frequent, no value).
 *     Only state-changing outcomes are written to MongoDB.
 *   - The traceId links this log entry back to the ActionDeliveryTracker so
 *     that when the device ACKs an action, the delivery status can be updated
 *     in the same document via AutomationLogStream.updateDeliveryStatus().
 *
 * MongoDB document example:
 * {
 *   "_id": "...",
 *   "automationId": "689f53b6...",
 *   "automationName": "Periodic Bat 500 charging",
 *   "user": "scheduler",
 *   "triggerDeviceId": "67dafae9...",
 *   "timestamp": ISODate("2026-05-15T10:30:00Z"),
 *   "payload": {"percent": 55, "key": "percent"},
 *   "status": "TRIGGERED",
 *   "reason": "TRIGGERED",
 *   "deliveryStatus": "DELIVERED",
 *   "deliveredAt": ISODate("2026-05-15T10:30:01Z"),
 *   "traceId": "689f53b6-1747302600123-a3f9e201",
 *   "evalDurationMs": 14
 * }
 */
public class AutomationLog {

    @Id
    private String id;

    /**
     * The automation that produced this log entry.
     */
    @Indexed
    private String automationId;

    /**
     * Denormalized name — avoids a JOIN when displaying the log UI.
     */
    private String automationName;

    /**
     * Who or what triggered this evaluation:
     * "scheduler"       — periodic 12s tick
     * "device:{id}"     — live device event
     * "<userId>"        — manual trigger from UI
     */
    private String user;
    private List<ConditionResult> conditionResults;
    /**
     * Device whose data change initiated the evaluation.
     */
    private String triggerDeviceId;

    /**
     * Wall-clock time when the evaluation ran.
     */
    @Indexed
    private Date timestamp;

    /**
     * Snapshot of the device payload that was evaluated.
     * Stored for audit / replay purposes.
     * For large payloads (e.g. WLED colour arrays) only the keys
     * relevant to the trigger are kept.
     */
    private Map<String, Object> payload;

    /**
     * Outcome of the evaluation tick.
     * Only TRIGGERED, RESTORED, TRIGGER_FALSE outcomes are persisted
     * to MongoDB — SKIPPED and NOT_MET are discarded.
     */
    private LogStatus status;

    /**
     * Human-readable description of why this outcome was produced.
     * Examples:
     * "TRIGGERED"
     * "Gate no longer true"
     * "Duration expired — RUNNING key gone"
     * "Snoozed — 4min remaining"
     */
    private String reason;

    /**
     * Delivery tracking status — updated asynchronously when the device ACKs
     * the action (via ActionDeliveryTracker → AutomationLogStream.updateDeliveryStatus).
     * Default: PENDING for actionable outcomes, NOT_APPLICABLE for notify-only.
     */
    private DeliveryStatus deliveryStatus;

    /**
     * Timestamp when deliveryStatus changed to DELIVERED or DELIVERY_FAILED.
     */
    private Date deliveredAt;

    /**
     * Unique trace identifier linking this log entry to:
     * - The action dispatch chain in ActionDispatcher
     * - The ActionDeliveryTracker's correlation map
     * - The device ACK payload (_cid field)
     * Format: {automationId[0..8]}-{epochMs}-{random 32-bit hex}
     */
    @Indexed
    private String traceId;

    /**
     * How long the pure evaluation phase (AutomationEvaluator.evaluate()) took.
     * Does NOT include dispatch time. Used to detect slow condition evaluation
     * (e.g. slow Redis reads, stale secondary device data).
     */
    private Long evalDurationMs;


    // ─────────────────────────────────────────────────────────────────────
    // ENUMS
    // ─────────────────────────────────────────────────────────────────────

    public enum LogStatus {
        /**
         * The automation's positive actions were dispatched.
         * Covers all triggering outcomes: direct trigger, stateless fire, fallback.
         */
        TRIGGERED,

        /**
         * A previously-triggered state was reverted — negative actions dispatched.
         * Covers DURATION_EXPIRED (interval ran for N minutes → off) and
         * REVERT (gate turned false / lower priority lost).
         */
        RESTORED,

        /**
         * The trigger condition (c1) turned false while the automation was active.
         * Negative / revert actions dispatched. State → IDLE.
         * <p>
         * Real-life: percent rose above 65 while "Periodic Bat 500 charging" was
         * HOLDING → channel1=false fired, automation went IDLE.
         */
        TRIGGER_FALSE,

        /**
         * Evaluation ran but no action was needed (already in correct state).
         * NOT written to MongoDB — filtered out in AutomationLogStream.publish().
         */
        SKIPPED,

        /**
         * Condition evaluated to false and nothing was previously active.
         * NOT written to MongoDB.
         */
        NOT_MET,

        /**
         * A branch gate was true but a higher-priority branch won, or AND
         * siblings were not met.  NOT written to MongoDB.
         */
        SUPPRESSED,

        /**
         * Evaluation was skipped (snooze, timed-disable, CAS conflict, missing data).
         * Written to MongoDB only for operator-visible skip reasons (snooze, disable).
         * CAS conflicts are not persisted (too noisy).
         */
        SKIP_REASON,
        USER_OVERRIDE
    }

    public enum DeliveryStatus {
        /**
         * Action was dispatched but no ACK received yet.
         */
        PENDING,

        /**
         * Device ACK received — action was confirmed delivered.
         */
        DELIVERED,

        /**
         * Dispatch failed or ACK timed out.
         */
        DELIVERY_FAILED,

        /**
         * No device ACK expected for this action type:
         * - alert / app_notify actions (in-app notification only)
         * - WLED actions (no ACK protocol currently implemented)
         */
        NOT_APPLICABLE
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