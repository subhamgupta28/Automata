package dev.automata.automata.automation_engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.automata.automata.dto.AutomationRuntimeState;
import dev.automata.automata.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Manages AutomationRuntimeState in Redis with optimistic CAS semantics.
 * <p>
 * Key schema (all keys registered in AUTOMATION_KEYS:<id> for bulk cleanup):
 * AUTOMATION_STATE:<id>             → JSON of AutomationRuntimeState (TTL 24h)
 * AUTOMATION_DEF:<id>               → JSON of ExecutionPlan (no TTL)
 * AUTOMATION_KEYS:<id>              → Redis SET of all keys owned by this automation
 * INTERVAL:<id>:<nodeId>            → "run" (TTL = intervalMinutes * 60)
 * RUNNING:<id>:<nodeId>             → "active" (TTL = durationMinutes * 60)
 * DAILY_FIRE:<id>:<YYYY-MM-DD>      → "fired" (TTL = seconds until midnight)
 * DAILY_SOLAR:<id>:<YYYY-MM-DD>     → "done" (TTL = seconds until midnight)
 * DAILY_INTERVAL:<id>:<nodeId>:<date> → "1" (TTL = seconds until midnight)
 * <p>
 * ─────────────────────────────────────────────────────────────────────────────
 * KEY CHANGES vs previous version
 * ─────────────────────────────────────────────────────────────────────────────
 * <p>
 * 1. @Scheduled testing() REMOVED (was firing every 30s in production)
 * <p>
 * 2. ObjectMapper INJECTED (not new ObjectMapper())
 * Old: private final ObjectMapper objectMapper = new ObjectMapper()
 * — bypassed the Spring-managed bean; any custom serializers
 * (JSR310, custom date formats, Jackson modules) were missing.
 * New: injected via constructor — same instance used by the rest of
 * the application. @RequiredArgsConstructor handles injection.
 * <p>
 * 3. AUTOMATION_KEYS registry set gets a TTL refreshed on every write
 * Old: registerKey() called opsForSet().add() with no TTL.
 * If an automation was force-deleted from MongoDB without calling
 * deleteAllKeys(), the registry set leaked in Redis indefinitely.
 * New: registerKey() also calls expire() to refresh the TTL to 25h
 * (slightly longer than the state TTL of 24h so the registry
 * always outlives the state it tracks).
 * <p>
 * 4. dailyIntervalKey schema made consistent with other daily key schemas
 * Old: "daily:interval:<id>:<nodeId>:<date>" (lowercase, colon prefix)
 * New: "DAILY_INTERVAL:<id>:<nodeId>:<date>" (uppercase, consistent)
 * All keys now follow UPPER_SNAKE_CASE:<id>:<discriminator> pattern.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutomationStateStore {

    private final RedisService redisService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;           // ← injected, not new

    private static final String STATE_PREFIX = "AUTOMATION_STATE:";
    private static final String DEF_PREFIX = "AUTOMATION_DEF:";
    private static final String KEYS_PREFIX = "AUTOMATION_KEYS:";
    private static final String VERSION_PREFIX = "AUTOMATION_DEF_VERSION:";
    private static final long STATE_TTL_S = 86_400L;   // 24h
    private static final long REGISTRY_TTL_S = 90_000L;   // 25h — outlives state

    /**
     * Lua CAS script.
     * KEYS[1] = state key
     * ARGV[1] = expected version
     * ARGV[2] = new state JSON
     * ARGV[3] = TTL seconds
     * Returns 1 on success, 0 on version mismatch.
     */
    private static final DefaultRedisScript<Long> CAS_SCRIPT = new DefaultRedisScript<>("""
            local raw = redis.call('GET', KEYS[1])
            if raw then
                local ver = cjson.decode(raw).version
                if tostring(ver) ~= ARGV[1] then
                    return 0
                end
            else
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
     * Read current runtime state. Returns AutomationRuntimeState.idle() when
     * no state exists yet. Plain GET — no lock, no CAS.
     */
    public AutomationRuntimeState read(String automationId) {
        try {
            Object raw = redisService.get(STATE_PREFIX + automationId);
            if (raw == null) return AutomationRuntimeState.idle();
            return objectMapper.readValue(raw.toString(), AutomationRuntimeState.class);
        } catch (Exception e) {
            log.warn("⚠️ Failed to read state for '{}': {} — returning IDLE",
                    automationId, e.getMessage());
            return AutomationRuntimeState.idle();
        }
    }


    // ─────────────────────────────────────────────────────────────────────
    // WRITE (CAS)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Atomically write nextState if the current stored version matches expectedVersion.
     *
     * @return true if write succeeded; false if version mismatch (stale read — caller retries)
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
            log.debug("⚡ CAS miss for '{}' — version {} is stale", automationId, expectedVersion);
            return false;
        } catch (Exception e) {
            log.error("❌ CAS write failed for '{}': {}", automationId, e.getMessage());
            return false;
        }
    }

    /**
     * Force-write state without version check.
     * Used only during startup reconciliation and after re-save in editor.
     */
    public void forceWrite(String automationId, AutomationRuntimeState state) {
        try {
            String stateKey = STATE_PREFIX + automationId;
            String json = objectMapper.writeValueAsString(state);
            redisService.setWithExpiry(stateKey, json, STATE_TTL_S);
            registerKey(automationId, stateKey);
        } catch (Exception e) {
            log.error("❌ Force-write failed for '{}': {}", automationId, e.getMessage());
        }
    }


    // ─────────────────────────────────────────────────────────────────────
    // EXECUTION PLAN  (AUTOMATION_DEF)
    // ─────────────────────────────────────────────────────────────────────

    public void writePlan(String automationId, ExecutionPlan plan) {
        try {
            String key = DEF_PREFIX + automationId;
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(plan));
            registerKey(automationId, key);

            // Cheap version marker — lets nodes self-heal from a missed pub/sub
            // eviction without paying the cost of deserializing the full plan
            // on every evaluation. Stamped as compiledAt's epoch millis so it's
            // monotonic with the plan itself (no separate counter to keep in sync).
            String versionKey = VERSION_PREFIX + automationId;
            long stamp = plan.getCompiledAt() != null ? plan.getCompiledAt().getTime() : 0L;
            redisTemplate.opsForValue().set(versionKey, String.valueOf(stamp));
            registerKey(automationId, versionKey);
        } catch (Exception e) {
            log.error("❌ Failed to write plan for '{}': {}", automationId, e.getMessage());
        }
    }

    /**
     * Returns the compiledAt epoch-millis stamp for the latest plan written
     * to Redis, or -1 if no plan/version key exists.
     * <p>
     * Cheap (single GET of a tiny string value) compared to readPlan(), so
     * it's safe to call on every evaluation tick to detect a missed pub/sub
     * invalidation: if this value doesn't match the locally-cached plan's
     * compiledAt, the local cache is stale and must be refreshed via readPlan().
     */
    public long readPlanVersion(String automationId) {
        try {
            Object raw = redisTemplate.opsForValue().get(VERSION_PREFIX + automationId);
            return raw != null ? Long.parseLong(raw.toString()) : -1L;
        } catch (Exception e) {
            log.warn("⚠️ Failed to read plan version for '{}': {}", automationId, e.getMessage());
            return -1L;
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
    // SCHEDULE KEYS  — INTERVAL
    // ─────────────────────────────────────────────────────────────────────

    public boolean intervalKeyExists(String automationId, String condNodeId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(intervalKey(automationId, condNodeId)));
    }

    /**
     * Arms the interval cooldown.
     * Called by AutomationOrchestrator AFTER a successful CAS write (not inside evaluator).
     * TTL = intervalMinutes * 60 so the key expires exactly when the next run is due.
     */
    public void setIntervalKey(String automationId, String condNodeId, long ttlSeconds) {
        String key = intervalKey(automationId, condNodeId);
        redisService.setWithExpiry(key, "run", ttlSeconds);
        registerKey(automationId, key);
        log.debug("🔑 SET intervalKey '{}' TTL={}s", key, ttlSeconds);
    }

    public void deleteIntervalKey(String automationId, String condNodeId) {
        redisTemplate.delete(intervalKey(automationId, condNodeId));
    }


    // ─────────────────────────────────────────────────────────────────────
    // SCHEDULE KEYS  — RUNNING (duration window)
    // ─────────────────────────────────────────────────────────────────────

    public boolean runningKeyExists(String automationId, String condNodeId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(runningKey(automationId, condNodeId)));
    }

    /**
     * Arms the duration window.
     * Called by dispatchBranchDecisions AFTER dispatch confirms success —
     * so the timer starts only when the device actually received the command.
     * TTL = durationMinutes * 60.
     */
    public void setRunningKey(String automationId, String condNodeId, long ttlSeconds) {
        String key = runningKey(automationId, condNodeId);
        redisService.setWithExpiry(key, "active", ttlSeconds);
        registerKey(automationId, key);
        log.debug("🔑 SET runningKey '{}' TTL={}s", key, ttlSeconds);
    }

    public void deleteRunningKey(String automationId, String condNodeId) {
        redisTemplate.delete(runningKey(automationId, condNodeId));
    }

    public void deleteIntervalAndRunningKeys(String automationId, String condNodeId) {
        redisTemplate.delete(List.of(
                intervalKey(automationId, condNodeId),
                runningKey(automationId, condNodeId)));
    }
// ── Negative-action grace window (generic, any condition with durationMinutes>0) ──

    private String graceKey(String automationId, String nodeId) {
        return "automata:grace:" + automationId + ":" + nodeId;
    }

    /**
     * Returns the epoch-ms when grace was armed, or null if not armed.
     */
    public Long getGraceArmedAtEpochMs(String automationId, String nodeId) {
        Object v = redisTemplate.opsForValue().get(graceKey(automationId, nodeId));
        if (v == null) return null;
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Arms the grace timer once. ttlSeconds should be durationMinutes*60 + small buffer.
     */
    public void armGrace(String automationId, String nodeId, long nowMs, long ttlSeconds) {
        redisTemplate.opsForValue().set(graceKey(automationId, nodeId),
                String.valueOf(nowMs), ttlSeconds, java.util.concurrent.TimeUnit.SECONDS);
    }

    public void clearGrace(String automationId, String nodeId) {
        redisTemplate.delete(graceKey(automationId, nodeId));
    }

    // ─────────────────────────────────────────────────────────────────────
    // SCHEDULE KEYS  — DAILY FIRE (at / exact-time schedules)
    // ─────────────────────────────────────────────────────────────────────

    public boolean dailyFireKeyExists(String automationId, String date) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(dailyFireKey(automationId, date)));
    }

    public void setDailyFireKey(String automationId, String date, long ttlSeconds) {
        String key = dailyFireKey(automationId, date);
        redisService.setWithExpiry(key, "fired", ttlSeconds);
        registerKey(automationId, key);
    }


    // ─────────────────────────────────────────────────────────────────────
    // SCHEDULE KEYS  — DAILY SOLAR (sunrise / sunset schedules)
    // ─────────────────────────────────────────────────────────────────────

    public boolean dailySolarKeyExists(String automationId, String date) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(dailySolarKey(automationId, date)));
    }

    public void setDailySolarKey(String automationId, String date, long ttlSeconds) {
        String key = dailySolarKey(automationId, date);
        redisService.setWithExpiry(key, "done", ttlSeconds);
        registerKey(automationId, key);
    }


    // ─────────────────────────────────────────────────────────────────────
    // SCHEDULE KEYS  — DAILY INTERVAL (once-per-day cap for interval schedules)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Sets a marker indicating this interval automation has already fired today.
     * The key expires at midnight (TTL = seconds until tomorrow 00:00 IST).
     * <p>
     * Used by AutomationOrchestrator.writePostCasScheduleKeys() — written AFTER
     * a successful CAS write, not inside the evaluator.
     * <p>
     * Real-life: "Periodic Bat 500 charging" runs every 30min all day.
     * If the intent is to cap at one run per calendar day, check this key.
     * If the intent is to run multiple times per day (just with 30min gaps),
     * don't check this key — rely solely on intervalKey for cooldown.
     */
    public void setDailyIntervalKey(String automationId, String nodeId,
                                    String date, long ttlSeconds) {
        String key = dailyIntervalKey(automationId, nodeId, date);
        redisService.setWithExpiry(key, "1", ttlSeconds);
        registerKey(automationId, key);
        log.debug("🔑 SET dailyIntervalKey '{}' TTL={}s", key, ttlSeconds);
    }

    public boolean dailyIntervalKeyExists(String automationId, String nodeId, String date) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(dailyIntervalKey(automationId, nodeId, date)));
    }

    public void deleteDailyIntervalKey(String automationId, String nodeId, String date) {
        redisTemplate.delete(dailyIntervalKey(automationId, nodeId, date));
    }

    public long getDailyIntervalKeyTTL(String automationId, String nodeId, String date) {
        return redisTemplate.getExpire(dailyIntervalKey(automationId, nodeId, date),
                TimeUnit.SECONDS);
    }


    // ─────────────────────────────────────────────────────────────────────
    // SNOOZE / TIMED DISABLE
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
     * Refreshes the registry set TTL so it always outlives the state TTL.
     * <p>
     * Bug fix: old code called opsForSet().add() with no TTL. If an automation
     * was force-deleted from MongoDB without calling deleteAllKeys(), the registry
     * set leaked in Redis forever. Now the TTL is refreshed on every registerKey()
     * so it expires ~25h after the last activity.
     */
    private void registerKey(String automationId, String key) {
        String registryKey = KEYS_PREFIX + automationId;
        redisTemplate.opsForSet().add(registryKey, key);
        redisTemplate.expire(registryKey, Duration.ofSeconds(REGISTRY_TTL_S));
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
        // Also delete well-known keys that may not be in the registry (backward compat)
        redisTemplate.delete(List.of(
                "SNOOZE:" + automationId,
                "TIMED_DISABLE:" + automationId,
                STATE_PREFIX + automationId,
                DEF_PREFIX + automationId,
                VERSION_PREFIX + automationId));
    }


    // ─────────────────────────────────────────────────────────────────────
    // KEY BUILDERS  (single source of truth for key schema)
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

    /**
     * Key schema changed from "daily:interval:<id>:<nodeId>:<date>"
     * (lowercase, colon prefix) to "DAILY_INTERVAL:<id>:<nodeId>:<date>"
     * for consistency with all other key schemas.
     * If migrating a live Redis instance, flush old "daily:interval:*" keys.
     */
    private String dailyIntervalKey(String automationId, String nodeId, String date) {
        return "DAILY_INTERVAL:" + automationId + ":" + nodeId + ":" + date;
    }

    public void snooze(String automationId, long ttlSeconds) {
        String key = "SNOOZE:" + automationId;
        redisService.setWithExpiry(key, "snoozed", ttlSeconds);
        registerKey(automationId, key);
        log.info("😴 Snooze armed for '{}' — {}s", automationId, ttlSeconds);
    }

    /**
     * Clears the snooze key immediately.
     * Called by AutomationInspectionController.clearSnooze().
     */
    public void clearSnooze(String automationId) {
        redisTemplate.delete("SNOOZE:" + automationId);
        log.info("⏰ Snooze cleared for '{}'", automationId);
    }

    /**
     * Arms the timed-disable key for the given duration.
     * While timed-disabled, orchestrator.execute() skips evaluation entirely.
     * Different from snooze in that it's intended for maintenance windows, not
     * user-initiated quiet periods.
     * <p>
     * Key: TIMED_DISABLE:<automationId>
     * TTL: ttlSeconds
     */
    public void timedDisable(String automationId, long ttlSeconds) {
        String key = "TIMED_DISABLE:" + automationId;
        redisService.setWithExpiry(key, "disabled", ttlSeconds);
        registerKey(automationId, key);
        log.info("🔕 Timed-disable armed for '{}' — {}s", automationId, ttlSeconds);
    }

    /**
     * Clears the timed-disable key immediately.
     */
    public void clearTimedDisable(String automationId) {
        redisTemplate.delete("TIMED_DISABLE:" + automationId);
        log.info("✅ Timed-disable cleared for '{}'", automationId);
    }
}