package dev.automata.automata.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.automata.automata.dto.AutomationRuntimeState;
import dev.automata.automata.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Manages AutomationRuntimeState in Redis with optimistic CAS semantics.
 * <p>
 * Key schema:
 * AUTOMATION_STATE:<automationId>   → JSON of AutomationRuntimeState (TTL 24h)
 * AUTOMATION_DEF:<automationId>     → JSON of ExecutionPlan (no TTL, invalidated on save)
 * AUTOMATION_KEYS:<automationId>    → Redis SET of all keys owned by this automation
 * Used for bulk cleanup on deletion.
 * <p>
 * CAS write: Lua script atomically checks the version field before writing.
 * If the version doesn't match (another node updated state between our read and
 * write), the script returns 0 and the caller retries with the latest state.
 * This replaces the blunt 60-second EXEC_LOCK with a non-blocking approach
 * that is safe across multiple JVM nodes sharing Redis.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutomationStateStore {

    private final RedisService redisService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String STATE_PREFIX = "AUTOMATION_STATE:";
    private static final String KEYS_PREFIX = "AUTOMATION_KEYS:";
    private static final long STATE_TTL_S = 86_400L; // 24 hours

    /**
     * Lua script for atomic CAS write.
     * KEYS[1] = state key
     * ARGV[1] = expected version (long as string)
     * ARGV[2] = new state JSON
     * ARGV[3] = TTL in seconds
     * Returns 1 on success, 0 on version mismatch (stale read).
     */
    private static final DefaultRedisScript<Long> CAS_SCRIPT = new DefaultRedisScript<>("""
            local raw = redis.call('GET', KEYS[1])
            if raw then
                local ver = cjson.decode(raw).version
                if tostring(ver) ~= ARGV[1] then
                    return 0
                end
            else
                -- Key doesn't exist yet — only allow if expected version is 0
                if ARGV[1] ~= '0' then
                    return 0
                end
            end
            redis.call('SET', KEYS[1], ARGV[2], 'EX', ARGV[3])
            return 1
            """, Long.class);


    // ─────────────────────────────────────────────────────────────────────
    // READ
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Read current runtime state for an automation.
     * Returns AutomationRuntimeState.idle() if no state exists yet.
     * This is a plain GET — no lock, no CAS — reads are always safe.
     */
    public AutomationRuntimeState read(String automationId) {
        try {
            Object raw = redisService.get(STATE_PREFIX + automationId);
            if (raw == null) return AutomationRuntimeState.idle();
            return objectMapper.readValue(raw.toString(), AutomationRuntimeState.class);
        } catch (Exception e) {
            log.warn("⚠️ Failed to read state for automation '{}': {} — returning IDLE",
                    automationId, e.getMessage());
            return AutomationRuntimeState.idle();
        }
    }


    // ─────────────────────────────────────────────────────────────────────
    // WRITE (CAS)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Atomically write nextState if the current stored version matches
     * expectedVersion.
     *
     * @return true  — write succeeded (state updated)
     * false — version mismatch (another node wrote first); caller should
     * re-read and re-evaluate.
     */
    public boolean compareAndSet(String automationId,
                                 long expectedVersion,
                                 AutomationRuntimeState nextState) {
        try {
            String stateKey = STATE_PREFIX + automationId;
            String json = objectMapper.writeValueAsString(nextState);

            Long result = redisTemplate.execute(
                    CAS_SCRIPT,
                    List.of(stateKey),
                    String.valueOf(expectedVersion),
                    json,
                    String.valueOf(STATE_TTL_S));

            if (result != null && result == 1L) {
                registerKey(automationId, stateKey);
                return true;
            }
            log.debug("⚡ CAS miss for automation '{}' — version {} is stale",
                    automationId, expectedVersion);
            return false;
        } catch (Exception e) {
            log.error("❌ CAS write failed for automation '{}': {}", automationId, e.getMessage());
            return false;
        }
    }

    /**
     * Force-write state without version check.
     * Used only during startup reconciliation and cache refresh after save.
     */
    public void forceWrite(String automationId, AutomationRuntimeState state) {
        try {
            String stateKey = STATE_PREFIX + automationId;
            String json = objectMapper.writeValueAsString(state);
            redisService.setWithExpiry(stateKey, json, STATE_TTL_S);
            registerKey(automationId, stateKey);
        } catch (Exception e) {
            log.error("❌ Force-write failed for automation '{}': {}", automationId, e.getMessage());
        }
    }


    // ─────────────────────────────────────────────────────────────────────
    // EXECUTION PLAN (AUTOMATION_DEF)
    // ─────────────────────────────────────────────────────────────────────

    private static final String DEF_PREFIX = "AUTOMATION_DEF:";

    public void writePlan(String automationId, ExecutionPlan plan) {
        try {
            String key = DEF_PREFIX + automationId;
            String json = objectMapper.writeValueAsString(plan);
            // No TTL — invalidated only on save or deletion
            redisTemplate.opsForValue().set(key, json);
            registerKey(automationId, key);
        } catch (Exception e) {
            log.error("❌ Failed to write plan for '{}': {}", automationId, e.getMessage());
        }
    }

    public ExecutionPlan readPlan(String automationId) {
        try {
            Object raw = redisTemplate.opsForValue().get(DEF_PREFIX + automationId);
            if (raw == null) return null;
            return objectMapper.readValue(raw.toString(), ExecutionPlan.class);
        } catch (Exception e) {
            log.warn("⚠️ Failed to read plan for '{}': {}", automationId, e.getMessage());
            return null;
        }
    }

    public void deletePlan(String automationId) {
        redisTemplate.delete(DEF_PREFIX + automationId);
    }


    // ─────────────────────────────────────────────────────────────────────
    // SCHEDULE KEYS  (interval / running / daily — unchanged semantics)
    // ─────────────────────────────────────────────────────────────────────

    public boolean intervalKeyExists(String automationId, String condNodeId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(intervalKey(automationId, condNodeId)));
    }

    public void setIntervalKey(String automationId, String condNodeId, long ttlSeconds) {
        String key = intervalKey(automationId, condNodeId);
        redisService.setWithExpiry(key, "run", ttlSeconds);
        registerKey(automationId, key);
    }

    public boolean runningKeyExists(String automationId, String condNodeId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(runningKey(automationId, condNodeId)));
    }

    public void setRunningKey(String automationId, String condNodeId, long ttlSeconds) {
        String key = runningKey(automationId, condNodeId);
        redisService.setWithExpiry(key, "active", ttlSeconds);
        registerKey(automationId, key);
    }

    public void deleteRunningKey(String automationId, String condNodeId) {
        redisTemplate.delete(runningKey(automationId, condNodeId));
    }

    public boolean dailyFireKeyExists(String automationId, String date) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(dailyFireKey(automationId, date)));
    }

    public void setDailyFireKey(String automationId, String date, long ttlSeconds) {
        String key = dailyFireKey(automationId, date);
        redisService.setWithExpiry(key, "fired", ttlSeconds);
        registerKey(automationId, key);
    }

    public boolean dailySolarKeyExists(String automationId, String date) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(dailySolarKey(automationId, date)));
    }

    public void setDailySolarKey(String automationId, String date, long ttlSeconds) {
        String key = dailySolarKey(automationId, date);
        redisService.setWithExpiry(key, "done", ttlSeconds);
        registerKey(automationId, key);
    }

    public void deleteIntervalAndRunningKeys(String automationId, String condNodeId) {
        redisTemplate.delete(List.of(
                intervalKey(automationId, condNodeId),
                runningKey(automationId, condNodeId)));
    }


    // ─────────────────────────────────────────────────────────────────────
    // SNOOZE / TIMED DISABLE  (delegated, just key registration)
    // ─────────────────────────────────────────────────────────────────────

    public boolean isSnoozed(String automationId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("SNOOZE:" + automationId));
    }

    public boolean isTimedDisabled(String automationId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey("TIMED_DISABLE:" + automationId));
    }

    public Long snoozeTTL(String automationId) {
        return redisTemplate.getExpire("SNOOZE:" + automationId);
    }

    public Long timedDisableTTL(String automationId) {
        return redisTemplate.getExpire("TIMED_DISABLE:" + automationId);
    }


    // ─────────────────────────────────────────────────────────────────────
    // KEY REGISTRY  — bulk cleanup on automation deletion
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Registers a Redis key as owned by this automation.
     * Used by deleteAllKeys() to clean up everything on automation deletion.
     */
    private void registerKey(String automationId, String key) {
        redisTemplate.opsForSet().add(KEYS_PREFIX + automationId, key);
    }

    /**
     * Deletes ALL Redis keys owned by this automation.
     * Called from AutomationService.deleteAutomation().
     */
    public void deleteAllKeys(String automationId) {
        String registryKey = KEYS_PREFIX + automationId;
        Set<String> ownedKeys = redisTemplate.opsForSet().members(registryKey);
        if (ownedKeys != null && !ownedKeys.isEmpty()) {
            redisTemplate.delete(ownedKeys);
            log.info("🧹 Deleted {} Redis keys for automation '{}'", ownedKeys.size(), automationId);
        }
        redisTemplate.delete(registryKey);
        // Also delete snooze/disable keys (may not have been registered if old code created them)
        redisTemplate.delete(List.of(
                "SNOOZE:" + automationId,
                "TIMED_DISABLE:" + automationId,
                STATE_PREFIX + automationId,
                DEF_PREFIX + automationId));
    }


    // ─────────────────────────────────────────────────────────────────────
    // KEY BUILDERS
    // ─────────────────────────────────────────────────────────────────────

    private String intervalKey(String automationId, String condNodeId) {
        return "INTERVAL:" + automationId + ":" + condNodeId;
    }

    private String runningKey(String automationId, String condNodeId) {
        return "RUNNING:" + automationId + ":" + condNodeId;
    }

    private String dailyFireKey(String automationId, String date) {
        return "DAILY_FIRE:" + automationId + ":" + date;
    }

    private String dailySolarKey(String automationId, String date) {
        return "DAILY_SOLAR:" + automationId + ":" + date;
    }
}
