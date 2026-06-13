package dev.automata.automata.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Manages the Redis-side ring buffer for in-flight recording data.
 * <p>
 * Key layout
 * ──────────
 * rec:buf:{sessionId}:{deviceId}   → Redis List  (RPUSH / LRANGE+LTRIM)
 * rec:active                       → Redis Set   of active sessionIds
 * rec:session:{sessionId}:devices  → Redis Set   of deviceIds being recorded
 * <p>
 * Readings are stored as JSON strings. The flush job pops them in bulk and
 * writes bucketed documents to MongoDB.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordingBufferService {

    private static final String ACTIVE_SESSIONS_KEY = "rec:active";
    private static final String BUF_PREFIX = "rec:buf:";
    private static final String DEVICES_PREFIX = "rec:session:";
    private static final String DEVICES_SUFFIX = ":devices";

    /**
     * Buffer TTL — safety net so Redis doesn't fill up if the flush job dies
     */
    private static final long BUFFER_TTL_SECONDS = 60 * 60 * 6; // 6 h

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    // ──────────────────────────────────────────────────────────────
    // SESSION LIFECYCLE
    // ──────────────────────────────────────────────────────────────

    public void activateSession(String sessionId, List<String> deviceIds) {
        redisTemplate.opsForSet().add(ACTIVE_SESSIONS_KEY, sessionId);
        if (deviceIds != null && !deviceIds.isEmpty()) {
            String devKey = deviceSetKey(sessionId);
            redisTemplate.opsForSet().add(devKey, deviceIds.toArray(new String[0]));
            redisTemplate.expire(devKey, BUFFER_TTL_SECONDS, TimeUnit.SECONDS);
        }
        log.info("🔴 [recording] Session '{}' activated in Redis, devices={}", sessionId, deviceIds);
    }

    public void deactivateSession(String sessionId) {
        redisTemplate.opsForSet().remove(ACTIVE_SESSIONS_KEY, sessionId);
        // Leave buffer key alive — flush job will drain it one final time
        log.info("⏹️ [recording] Session '{}' deactivated in Redis", sessionId);
    }

    public Set<String> getActiveSessions() {
        Set<String> s = redisTemplate.opsForSet().members(ACTIVE_SESSIONS_KEY);
        return s != null ? s : Set.of();
    }

    public Set<String> getDevicesForSession(String sessionId) {
        Set<String> s = redisTemplate.opsForSet().members(deviceSetKey(sessionId));
        return s != null ? s : Set.of();
    }

    // ──────────────────────────────────────────────────────────────
    // BUFFER WRITE (called from MqttService on every message)
    // ──────────────────────────────────────────────────────────────

    /**
     * Pushes one reading to the Redis list for (sessionId, deviceId).
     * Ensures the list has a TTL so stale data self-cleans.
     *
     * @param sessionId active session
     * @param deviceId  device that produced this reading
     * @param payload   raw MQTT payload (already has last_seen injected by MqttService)
     */
    public void push(String sessionId, String deviceId, Map<String, Object> payload) {
        String key = bufferKey(sessionId, deviceId);
        try {
            // Stamp with ts (epoch-ms) so bucket logic can group by second
            payload.put("ts", System.currentTimeMillis());
            String json = objectMapper.writeValueAsString(payload);
            redisTemplate.opsForList().rightPush(key, json);
            redisTemplate.expire(key, BUFFER_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            log.error("❌ [recording] Failed to serialize payload for session '{}' device '{}': {}",
                    sessionId, deviceId, e.getMessage());
        }
    }

    // ──────────────────────────────────────────────────────────────
    // BUFFER READ (called by flush job)
    // ──────────────────────────────────────────────────────────────

    /**
     * Atomically pops up to {@code maxCount} entries from the buffer.
     * Uses LRANGE to read, then LTRIM to remove — not truly atomic in Redis,
     * but the flush job is single-threaded per session so this is safe.
     * <p>
     * For true atomicity you can wrap in a Lua script; omitted here for clarity.
     */
    public List<Map<String, Object>> popBatch(String sessionId, String deviceId, int maxCount) {
        String key = bufferKey(sessionId, deviceId);
        Long size = redisTemplate.opsForList().size(key);
        if (size == null || size == 0) return List.of();

        int count = (int) Math.min(size, maxCount);
        List<String> raw = redisTemplate.opsForList().range(key, 0, count - 1);
        redisTemplate.opsForList().trim(key, count, -1); // remove what we just read

        List<Map<String, Object>> result = new ArrayList<>(count);
        if (raw == null) return result;

        for (String json : raw) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = objectMapper.readValue(json, Map.class);
                result.add(m);
            } catch (JsonProcessingException e) {
                log.warn("⚠️ [recording] Skipping corrupt buffer entry: {}", json);
            }
        }
        return result;
    }

    /**
     * Returns the current buffer depth (useful for monitoring).
     */
    public long bufferDepth(String sessionId, String deviceId) {
        Long s = redisTemplate.opsForList().size(bufferKey(sessionId, deviceId));
        return s != null ? s : 0;
    }

    /**
     * Deletes all buffer keys for a session (called after final flush).
     */
    public void cleanupSession(String sessionId) {
        // device set tells us which buffer keys exist
        Set<String> devices = getDevicesForSession(sessionId);
        for (String deviceId : devices) {
            redisTemplate.delete(bufferKey(sessionId, deviceId));
        }
        redisTemplate.delete(deviceSetKey(sessionId));
        log.info("🧹 [recording] Cleaned up Redis keys for session '{}'", sessionId);
    }

    // ──────────────────────────────────────────────────────────────
    // KEY HELPERS
    // ──────────────────────────────────────────────────────────────

    private String bufferKey(String sessionId, String deviceId) {
        return BUF_PREFIX + sessionId + ":" + deviceId;
    }

    private String deviceSetKey(String sessionId) {
        return DEVICES_PREFIX + sessionId + DEVICES_SUFFIX;
    }
}