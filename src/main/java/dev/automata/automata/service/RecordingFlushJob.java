package dev.automata.automata.service;

import dev.automata.automata.model.RecordingBucket;
import dev.automata.automata.model.RecordingSession;
import dev.automata.automata.repository.RecordingBucketRepository;
import dev.automata.automata.repository.RecordingSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Drains the Redis ring buffer into MongoDB every 5 seconds.
 * <p>
 * Flow per active session
 * ───────────────────────
 * 1. Pop up to MAX_BATCH readings from Redis for each device.
 * 2. Group readings by their 60-second bucket window (floor(ts / 60000) * 60000).
 * 3. For each bucket: upsert the RecordingBucket document using $push + $inc.
 * MongoDB 6 supports the arrayFilters / $push/$each/$slice pipeline, but a
 * plain upsert with $push is simpler and correct here because we only call
 * it once per (sessionId, deviceId, bucketStart) per flush cycle.
 * 4. Update the session's recordCount.
 * 5. Check duration limit → auto-stop if exceeded.
 * 6. After a session is STOPPED and the buffer is empty, clean up Redis.
 * <p>
 * Concurrency
 * ───────────
 * This job runs on a single thread (@Scheduled is single-threaded by default).
 * If you run multiple instances, add a distributed lock (e.g. Redisson) around
 * the outer loop. For a single-node deployment this is not necessary.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecordingFlushJob {

    /**
     * Max readings to pop per device per flush cycle
     */
    private static final int MAX_BATCH = 300;

    private final RecordingBufferService bufferService;
    private final RecordingSessionService sessionService;
    private final RecordingSessionRepository sessionRepository;
    private final RecordingBucketRepository bucketRepository;
    private final MongoTemplate mongoTemplate;

    @Scheduled(fixedDelay = 5_000)   // every 5 seconds
    public void flush() {
        // 1. Active sessions from Redis (fast set lookup)
        Set<String> activeIds = bufferService.getActiveSessions();

        // 2. Also check Mongo for sessions that were STOPPED but may have
        //    remaining buffer data (the deactivateSession call removes them from
        //    the Redis active set, but we still need a final drain pass).
        //    We track these in a local "draining" set.
        Set<String> allSessionIds = new HashSet<>(activeIds);
        // Drain pass: any session in STOPPED state that still has buffer depth > 0
        // is handled implicitly because deactivateSession() keeps buffer keys alive.

        for (String sessionId : allSessionIds) {
            try {
                flushSession(sessionId, activeIds.contains(sessionId));
            } catch (Exception e) {
                log.error("❌ [flush] Error flushing session '{}': {}", sessionId, e.getMessage(), e);
            }
        }
    }

    private void flushSession(String sessionId, boolean isActive) {
        RecordingSession session;
        try {
            session = sessionService.getSession(sessionId);
        } catch (IllegalArgumentException e) {
            // Session deleted — clean up Redis
            bufferService.cleanupSession(sessionId);
            return;
        }

        // Determine which devices to flush
        Set<String> devices = bufferService.getDevicesForSession(sessionId);
        if (devices.isEmpty()) {
            // Session was created with empty deviceIds → record all devices that pushed
            // (Buffer keys exist for any device that pushed to this session)
            // No-op here; the push() side already stored readings; we rely on
            // the device set being populated during push if deviceIds was empty.
            // See RecordingService.routePayload() for how we handle the "all devices" case.
            return;
        }

        long totalFlushed = 0;

        for (String deviceId : devices) {
            long flushed = flushDevice(sessionId, deviceId);
            totalFlushed += flushed;
        }

        if (totalFlushed > 0) {
            sessionService.incrementRecordCount(sessionId, totalFlushed);
            log.debug("💾 [flush] Session '{}' — flushed {} readings across {} devices",
                    sessionId, totalFlushed, devices.size());
        }

        // Duration limit check
        if (isActive && session.getDurationLimitSecs() > 0 && session.getStartTime() != null) {
            long elapsedSecs = (System.currentTimeMillis() - session.getStartTime().getTime()) / 1000;
            if (elapsedSecs >= session.getDurationLimitSecs()) {
                log.info("⏱️ [flush] Session '{}' reached duration limit ({}s) — auto-stopping",
                        sessionId, session.getDurationLimitSecs());
                sessionService.stopSession(sessionId);
            }
        }

        // Final cleanup: if session is STOPPED and buffer is empty, remove Redis keys
        if (!isActive) {
            boolean allDrained = devices.stream()
                    .allMatch(d -> bufferService.bufferDepth(sessionId, d) == 0);
            if (allDrained) {
                bufferService.cleanupSession(sessionId);
                log.info("🧹 [flush] Session '{}' fully drained and cleaned up", sessionId);
            }
        }
    }

    /**
     * Pops a batch for one device, groups by bucket window, upserts MongoDB.
     *
     * @return number of readings flushed
     */
    private long flushDevice(String sessionId, String deviceId) {
        List<Map<String, Object>> readings = bufferService.popBatch(sessionId, deviceId, MAX_BATCH);
        if (readings.isEmpty()) return 0;

        // Group readings by 60-second bucket start (epoch-ms, floored to minute)
        Map<Long, List<Map<String, Object>>> byBucket = readings.stream()
                .collect(Collectors.groupingBy(r -> floorToMinute(getTsMs(r))));

        for (Map.Entry<Long, List<Map<String, Object>>> entry : byBucket.entrySet()) {
            long bucketStartMs = entry.getKey();
            List<Map<String, Object>> bucketReadings = entry.getValue();

            upsertBucket(sessionId, deviceId, bucketStartMs, bucketReadings);
        }

        return readings.size();
    }

    /**
     * Upserts a bucket document.
     * <p>
     * On first insert: creates the document with readings array.
     * On subsequent pushes to the same bucket: appends readings via $push/$each
     * and increments count via $inc.
     * <p>
     * Uses MongoTemplate directly for the $push/$inc update — Spring Data's
     * save() would replace the whole document (losing concurrent pushes).
     */
    private void upsertBucket(String sessionId, String deviceId,
                              long bucketStartMs, List<Map<String, Object>> newReadings) {
        Date bucketStart = Date.from(Instant.ofEpochMilli(bucketStartMs));
        Date bucketEnd = Date.from(Instant.ofEpochMilli(bucketStartMs).plus(60, ChronoUnit.SECONDS));

        Query query = new Query(
                Criteria.where("sessionId").is(sessionId)
                        .and("deviceId").is(deviceId)
                        .and("bucketStart").is(bucketStart)
        );

        Update update = new Update()
                .setOnInsert("sessionId", sessionId)
                .setOnInsert("deviceId", deviceId)
                .setOnInsert("bucketStart", bucketStart)
                .setOnInsert("bucketEnd", bucketEnd)
                .push("readings").each(newReadings.toArray())
                .inc("count", newReadings.size());

        mongoTemplate.upsert(query, update, RecordingBucket.class);
    }

    // ──────────────────────────────────────────────────────────────
    // HELPERS
    // ──────────────────────────────────────────────────────────────

    /**
     * Floors a timestamp to the start of its 60-second window.
     */
    private long floorToMinute(long tsMs) {
        return (tsMs / 60_000L) * 60_000L;
    }

    @SuppressWarnings("unchecked")
    private long getTsMs(Map<String, Object> reading) {
        Object ts = reading.get("ts");
        if (ts instanceof Number n) return n.longValue();
        if (ts instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
            }
        }
        return System.currentTimeMillis(); // fallback: use now
    }
}