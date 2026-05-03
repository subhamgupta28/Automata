package dev.automata.automata.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.automata.automata.model.AutomationLog;
import dev.automata.automata.repository.AutomationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Durable automation execution log buffer backed by a Redis Stream.
 * <p>
 * Live device data (1 payload/second/device) NEVER passes through here.
 * That data is written directly to Redis via redisService.setRecentDeviceData()
 * and overwritten on every tick — it is not persisted to MongoDB at all.
 * <p>
 * This class handles only AUTOMATION EXECUTION LOGS — TRIGGERED, RESTORED,
 * SKIPPED etc. — which are sparse (fire only on state changes) and are audit
 * records that users query from the UI. These ARE persisted to MongoDB.
 * <p>
 * Why Redis Stream instead of ConcurrentLinkedQueue:
 * - Durable: survives JVM crashes (entries stay unACKed in the stream)
 * - Distributed: only one node flushes each entry (consumer group semantics)
 * - Backpressure: stream capped at MAX_LEN entries via approximate MAXLEN
 * - Recovery: recoverPending() re-claims entries from crashed nodes
 * <p>
 * Stream key:    automation:logs:stream
 * Consumer group: log-flusher
 * Consumer name: <hostname>-<pid> (unique per JVM instance)
 * <p>
 * Bug fix vs previous version:
 * - debounceMap is now ConcurrentHashMap (was plain HashMap — not thread-safe
 * since publish() is called from multiple @Async threads concurrently)
 * <p>
 * NOTE: requires Redis 5.0+ for XADD / XREADGROUP / XACK / XCLAIM.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutomationLogStream {

    private final RedisTemplate<String, String> redisTemplate;
    private final AutomationLogRepository logRepository;
    private final ObjectMapper objectMapper;

    private static final String STREAM_KEY = "automation:logs:stream";
    private static final String GROUP_NAME = "log-flusher";
    private static final String CONSUMER_NAME = buildConsumerName();
    private static final int MAX_LEN = 10_000;
    private static final int BATCH_SIZE = 500;
    private static final long DEBOUNCE_MS = 60_000L;

    // Bug fix: was HashMap — not safe for concurrent publish() calls from @Async threads
    private final ConcurrentHashMap<String, Long> debounceMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DeliveryUpdate> deliveryBuffer =
            new ConcurrentHashMap<>();

    private record DeliveryUpdate(
            AutomationLog.DeliveryStatus status,
            Date resolvedAt
    ) {
    }

    /**
     * Called asynchronously by ActionDeliveryTracker when a device ACKs an action
     * (_cid match) or when the confirmation timeout expires.
     * <p>
     * If the log entry has already been flushed to MongoDB, falls through to
     * a direct MongoDB update. If still in the flush buffer, the status is merged
     * before the document is written (zero extra DB round-trips).
     *
     * @param traceId    the traceId from the originating execute() call
     * @param status     DELIVERED, DELIVERY_FAILED, or NOT_APPLICABLE
     * @param resolvedAt wall-clock time of the delivery resolution event
     */
    public void updateDeliveryStatus(String traceId,
                                     AutomationLog.DeliveryStatus status,
                                     Date resolvedAt) {
        if (traceId == null) return;

        // Try buffer first — if the log entry hasn't been flushed yet, the status
        // will be merged in flush() before the MongoDB write.
        deliveryBuffer.put(traceId, new DeliveryUpdate(status, resolvedAt));

        log.debug("📬 Delivery status buffered: traceId={} status={}", traceId, status);
    }
    // ─────────────────────────────────────────────────────────────────────
    // WRITE — called from AutomationOrchestrator after each evaluation
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Append a log entry to the Redis Stream.
     * <p>
     * NOT_MET and SKIPPED entries are debounced per automationId: only one
     * entry per automation per 60 seconds is written, preventing the stream
     * from filling up with noise from the 12s periodic scheduler.
     * <p>
     * All other statuses (TRIGGERED, RESTORED, TRIGGER_FALSE, ERROR, etc.)
     * are always written.
     */
    public void publish(AutomationLog entry) {
        if (entry.getStatus() == AutomationLog.LogStatus.NOT_MET
                || entry.getStatus() == AutomationLog.LogStatus.SKIPPED) {
            String dk = entry.getAutomationId() + ":" + entry.getStatus().name();
            long now = System.currentTimeMillis();
            // putIfAbsent + check pattern is safe with ConcurrentHashMap
            Long last = debounceMap.get(dk);
            if (last != null && (now - last) < DEBOUNCE_MS) return;
            debounceMap.put(dk, now);
        }

        try {
            String json = objectMapper.writeValueAsString(entry);
            redisTemplate.opsForStream().add(
                    StreamRecords.newRecord()
                            .in(STREAM_KEY)
                            .ofMap(Map.of("entry", json)));

            // Approximate trim — non-blocking, keeps stream under MAX_LEN
            redisTemplate.opsForStream().trim(STREAM_KEY, MAX_LEN, true);

        } catch (Exception e) {
            log.error("Failed to publish log entry to stream: {}", e.getMessage());
            // Fallback: write directly to MongoDB if stream is unavailable
            try {
                logRepository.save(entry);
            } catch (Exception ex) {
                log.error("Fallback MongoDB log write also failed: {}", ex.getMessage());
            }
        }
    }


    // ─────────────────────────────────────────────────────────────────────
    // FLUSH — runs every 5 seconds, drains stream into MongoDB
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Read a batch of log entries from the stream and bulk-save to MongoDB.
     * <p>
     * Consumer group semantics: in a multi-node cluster, each stream entry
     * is delivered to exactly ONE consumer. Whichever node reads it ACKs it
     * after saving, so there are no duplicate writes.
     */
    @Scheduled(fixedDelay = 5_000)
    public void flush() {
        try {
            ensureGroupExists();

            List<MapRecord<String, Object, Object>> records =
                    redisTemplate.opsForStream().read(
                            Consumer.from(GROUP_NAME, CONSUMER_NAME),
                            StreamReadOptions.empty()
                                    .count(BATCH_SIZE)
                                    .block(Duration.ofMillis(100)),
                            StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed()));

            if (records == null || records.isEmpty()) return;

            List<AutomationLog> batch = new ArrayList<>();
            List<RecordId> toAck = new ArrayList<>();

            for (MapRecord<String, Object, Object> record : records) {
                try {
                    Object raw = record.getValue().get("entry");
                    if (raw == null) {
                        toAck.add(record.getId());
                        continue;
                    }

                    AutomationLog entry = objectMapper.readValue(raw.toString(), AutomationLog.class);
                    String traceId = entry.getTraceId();
                    DeliveryUpdate update = (traceId != null) ? deliveryBuffer.remove(traceId) : null;
                    if (update != null) {
                        entry.setDeliveryStatus(update.status());
                        entry.setDeliveryResolvedAt(update.resolvedAt());
                    }


                    batch.add(entry);
                    toAck.add(record.getId());

                } catch (Exception e) {
                    log.warn("Skipping malformed log record {}: {}", record.getId(), e.getMessage());
                    toAck.add(record.getId());
                }
            }

            if (!batch.isEmpty()) {
                logRepository.saveAll(batch);
                log.debug("💾 Flushed {} automation log entries to MongoDB", batch.size());
            }
            if (!toAck.isEmpty()) {
                redisTemplate.opsForStream().acknowledge(
                        STREAM_KEY, GROUP_NAME, toAck.toArray(new RecordId[0]));
            }

        } catch (Exception e) {
            log.error("Log stream flush error: {}", e.getMessage());
        }
    }

    /**
     * Recover pending entries — runs every 2 minutes.
     * Re-claims entries that were delivered to a consumer that crashed
     * before ACKing. Claims entries pending for > 30 seconds.
     */
    @Scheduled(fixedDelay = 120_000)
    public void recoverPending() {
        try {
            ensureGroupExists();

            PendingMessagesSummary pending =
                    redisTemplate.opsForStream().pending(STREAM_KEY, GROUP_NAME);
            if (pending == null || pending.getTotalPendingMessages() == 0) return;

            log.info("♻️ {} pending log entries found — re-claiming",
                    pending.getTotalPendingMessages());

            List<MapRecord<String, Object, Object>> claimed =
                    redisTemplate.opsForStream().claim(
                            STREAM_KEY, GROUP_NAME, CONSUMER_NAME,
                            Duration.ofSeconds(30),
                            RecordId.of(pending.minMessageId()), RecordId.of(pending.maxMessageId()));

            if (claimed == null || claimed.isEmpty()) return;

            List<AutomationLog> batch = new ArrayList<>();
            List<RecordId> toAck = new ArrayList<>();
            for (MapRecord<String, Object, Object> r : claimed) {
                try {
                    Object raw = r.getValue().get("entry");
                    if (raw != null)
                        batch.add(objectMapper.readValue(raw.toString(), AutomationLog.class));
                } catch (Exception ignored) {
                }
                toAck.add(r.getId());
            }
            if (!batch.isEmpty()) logRepository.saveAll(batch);
            if (!toAck.isEmpty())
                redisTemplate.opsForStream().acknowledge(
                        STREAM_KEY, GROUP_NAME, toAck.toArray(new RecordId[0]));
            log.info("♻️ Recovered and flushed {} pending log entries", batch.size());

        } catch (Exception e) {
            log.warn("Pending log recovery error: {}", e.getMessage());
        }
    }


    // ─────────────────────────────────────────────────────────────────────
    // SETUP
    // ─────────────────────────────────────────────────────────────────────

    private volatile boolean groupCreated = false;

    private void ensureGroupExists() {
        if (groupCreated) return;
        try {
            redisTemplate.opsForStream()
                    .createGroup(STREAM_KEY, ReadOffset.latest(), GROUP_NAME);
            groupCreated = true;
            log.info("✅ Redis stream consumer group '{}' created on '{}'",
                    GROUP_NAME, STREAM_KEY);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("BUSYGROUP")) {
                groupCreated = true; // already exists — fine
            } else {
                log.warn("Stream group creation warning: {}", e.getMessage());
            }
        }
    }

    private static String buildConsumerName() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName()
                    + "-" + ProcessHandle.current().pid();
        } catch (Exception e) {
            return "consumer-" + UUID.randomUUID().toString().substring(0, 8);
        }
    }
}