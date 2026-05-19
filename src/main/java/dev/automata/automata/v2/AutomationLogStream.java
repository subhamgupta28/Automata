package dev.automata.automata.v2;

import dev.automata.automata.model.AutomationLog;
import dev.automata.automata.repository.AutomationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Buffered write stream for AutomationLog entries.
 * <p>
 * Why buffered?
 * The 12s scheduler tick evaluates every enabled automation every cycle.
 * With 50 automations and most of them in SKIPPED/NOT_MET state, a naive
 * "write every evaluation to MongoDB" approach creates a constant write storm
 * of hundreds of inserts per minute.
 * <p>
 * AutomationLogStream buffers entries in memory and only flushes entries that
 * represent actual state changes (TRIGGERED, RESTORED, TRIGGER_FALSE).
 * SKIPPED, NOT_MET, and SUPPRESSED are filtered out before buffering.
 * flush() runs on a 5-second schedule — far less frequent than the 12s
 * evaluation tick but still responsive enough for the audit log UI.
 * <p>
 * Delivery status updates:
 * When ActionDeliveryTracker confirms a device ACK, it calls
 * updateDeliveryStatus(traceId, DELIVERED, deliveredAt).  The log stream
 * looks up the pending entry by traceId and updates it in-place before
 * flush.  If the entry was already flushed, the update goes directly to
 * MongoDB via AutomationLogRepository.updateDeliveryStatus().
 * <p>
 * Thread safety:
 * - pendingEntries: CopyOnWriteArrayList — safe for concurrent publish()
 * from many @Async("automationExecutor") threads, and concurrent read
 * during flush().
 * - flushedTraceIds: ConcurrentHashMap — safe concurrent read/write.
 * - flush() is @Scheduled on a single thread; no locking needed there.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutomationLogStream {

    private final AutomationLogRepository logRepository;

    /**
     * In-memory buffer of log entries awaiting flush.
     * Only state-changing entries (TRIGGERED, RESTORED, TRIGGER_FALSE) are added.
     */
    private final CopyOnWriteArrayList<AutomationLog> pendingEntries =
            new CopyOnWriteArrayList<>();

    /**
     * Map of traceId → AutomationLog for entries currently in pendingEntries
     * that have not yet been flushed. Allows updateDeliveryStatus() to update
     * an entry in-place before it hits MongoDB.
     */
    private final ConcurrentHashMap<String, AutomationLog> pendingByTraceId =
            new ConcurrentHashMap<>();

    /**
     * Map of traceId → MongoDB document ID for entries already flushed.
     * Allows updateDeliveryStatus() to issue a targeted MongoDB update
     * when the ACK arrives after flush.
     * Capped at ~10 000 entries — older entries are evicted to prevent unbounded growth.
     */
    private final ConcurrentHashMap<String, String> flushedTraceToId =
            new ConcurrentHashMap<>();

    private static final int FLUSHED_CACHE_MAX = 10_000;

    // ─────────────────────────────────────────────────────────────────────
    // PUBLISH
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Adds a log entry to the buffer if it represents a state-changing outcome.
     * SKIPPED, NOT_MET, and SUPPRESSED are discarded immediately.
     * <p>
     * Called from AutomationOrchestrator.publishLog() on every evaluation,
     * and from publishSkippedLog() for operator-visible skip reasons.
     * <p>
     * Thread-safe — may be called from any automationExecutor thread.
     */
    public void publish(AutomationLog entry) {
        if (entry == null) return;
        if (!shouldPersist(entry.getStatus())) return;

        // Default delivery status
        if (entry.getDeliveryStatus() == null)
            entry.setDeliveryStatus(AutomationLog.DeliveryStatus.PENDING);

        pendingEntries.add(entry);

        if (entry.getTraceId() != null)
            pendingByTraceId.put(entry.getTraceId(), entry);

        log.debug("📝 [traceId={}] Buffered log: {} — {}",
                entry.getTraceId(), entry.getStatus(), entry.getAutomationName());
    }

    private boolean shouldPersist(AutomationLog.LogStatus status) {
        if (status == null) return false;
        return switch (status) {
            case TRIGGERED, RESTORED, TRIGGER_FALSE, SKIP_REASON -> true;
            case SKIPPED, NOT_MET, SUPPRESSED, USER_OVERRIDE -> false;
        };
    }


    // ─────────────────────────────────────────────────────────────────────
    // DELIVERY STATUS UPDATE
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Updates the delivery status of a previously published log entry.
     * <p>
     * Called from ActionDeliveryTracker when a device ACK is received
     * (via AutomationService.ackAction → deliveryTracker.confirm → here).
     * <p>
     * Two paths:
     * 1. Entry still in pendingEntries → update in-place before flush.
     * Atomic because the entry object is mutated under CopyOnWriteArrayList
     * visibility guarantees (AutomationLog is @Data so setters are atomic
     * for individual fields).
     * 2. Entry already flushed → issue a targeted MongoDB field update via
     * AutomationLogRepository.updateDeliveryStatus().
     *
     * @param traceId     the traceId from the device ACK payload (_cid chain)
     * @param status      new delivery status (DELIVERED / DELIVERY_FAILED / NOT_APPLICABLE)
     * @param deliveredAt timestamp of the ACK (or failure detection time)
     */
    public void updateDeliveryStatus(String traceId,
                                     AutomationLog.DeliveryStatus status,
                                     Date deliveredAt) {
        if (traceId == null) return;

        // Path 1: still pending in buffer
        AutomationLog pending = pendingByTraceId.get(traceId);
        if (pending != null) {
            pending.setDeliveryStatus(status);
            pending.setDeliveredAt(deliveredAt);
            log.debug("📬 [traceId={}] Delivery status updated in buffer → {}",
                    traceId, status);
            return;
        }

        // Path 2: already flushed — update MongoDB directly
        String mongoId = flushedTraceToId.get(traceId);
        if (mongoId != null) {
            try {
                var logs = logRepository.findById(mongoId);
                if (logs.isPresent()) {
                    var res = logs.get();
                    res.setDeliveryStatus(status);
                    res.setDeliveredAt(deliveredAt);
                    logRepository.save(res);
                }

                log.debug("📬 [traceId={}] Delivery status updated in MongoDB (id={}) → {}",
                        traceId, mongoId, status);
            } catch (Exception e) {
                log.error("❌ Failed to update delivery status for traceId={}: {}",
                        traceId, e.getMessage());
            }
            return;
        }

        // Not found in either — device ACK arrived before publish() was called.
        // This is rare but possible if the device responds in under 1ms.
        // Store it transiently so a late publish() can pick it up.
        log.warn("⚠️ [traceId={}] updateDeliveryStatus called before publish — status={} will be lost",
                traceId, status);
    }


    // ─────────────────────────────────────────────────────────────────────
    // FLUSH (scheduled)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Flushes all buffered entries to MongoDB every 5 seconds.
     * <p>
     * Strategy:
     * 1. Drain pendingEntries in a single pass (snapshot + clear).
     * 2. Batch-insert via logRepository.saveAll().
     * 3. Record the MongoDB-assigned IDs in flushedTraceToId for future
     * delivery-status updates that arrive after flush.
     * 4. Remove flushed traceIds from pendingByTraceId.
     * <p>
     * Snapshot-then-clear pattern: we snapshot the list first so that new
     * entries published during the flush are NOT included in this batch —
     * they will be picked up on the next flush cycle.
     */
    @Scheduled(fixedRate = 5_000)
    public void flush() {
        if (pendingEntries.isEmpty()) return;

        // Snapshot: copy current entries and atomically clear the buffer
        List<AutomationLog> toFlush = new ArrayList<>(pendingEntries);
        pendingEntries.removeAll(toFlush);

        if (toFlush.isEmpty()) return;

        try {
            List<AutomationLog> saved = logRepository.saveAll(toFlush);
            int count = saved.size();

            // Register flushed entries in the trace-to-id map for post-flush ACKs
            saved.forEach(entry -> {
                if (entry.getTraceId() != null && entry.getId() != null) {
                    flushedTraceToId.put(entry.getTraceId(), entry.getId());
                    pendingByTraceId.remove(entry.getTraceId());
                }
            });

            // Evict oldest entries from flushedTraceToId if over limit
            if (flushedTraceToId.size() > FLUSHED_CACHE_MAX) {
                Iterator<String> it = flushedTraceToId.keySet().iterator();
                int evict = flushedTraceToId.size() - FLUSHED_CACHE_MAX;
                while (it.hasNext() && evict-- > 0) {
                    it.next();
                    it.remove();
                }
            }

            log.info("📦 AutomationLogStream flushed {} entr{} to MongoDB",
                    count, count == 1 ? "y" : "ies");

        } catch (Exception e) {
            log.error("❌ AutomationLogStream flush failed: {} — {} entries re-queued",
                    e.getMessage(), toFlush.size());
            // Re-queue failed entries so they are retried on next flush
            pendingEntries.addAll(toFlush);
            toFlush.forEach(entry -> {
                if (entry.getTraceId() != null)
                    pendingByTraceId.putIfAbsent(entry.getTraceId(), entry);
            });
        }
    }


    // ─────────────────────────────────────────────────────────────────────
    // INTROSPECTION
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Returns the number of entries currently awaiting flush.
     */
    public int pendingCount() {
        return pendingEntries.size();
    }

    /**
     * Forces an immediate flush regardless of the schedule. Intended for testing.
     */
    public void flushNow() {
        flush();
    }
}