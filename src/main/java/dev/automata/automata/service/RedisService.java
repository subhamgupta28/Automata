package dev.automata.automata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.automata.automata.dto.AutomationCache;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@AllArgsConstructor
public class RedisService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Lua script for atomic "set if not exists with TTL"
    private static final String LUA_SET_IF_ABSENT =
            "if redis.call('exists', KEYS[1]) == 0 then " +
                    "  redis.call('setex', KEYS[1], ARGV[1], ARGV[2]) " +
                    "  return 1 " +
                    "else " +
                    "  return 0 " +
                    "end";

    // Lua script for atomic "delete if value matches"
    private static final String LUA_DELETE_IF_EQUALS =
            "if redis.call('get', KEYS[1]) == ARGV[1] then " +
                    "  return redis.call('del', KEYS[1]) " +
                    "else " +
                    "  return 0 " +
                    "end";

    /**
     * HIGH PRIORITY: Distributed lock - set if absent with TTL
     *
     * @param key        Lock key
     * @param value      Lock value (typically UUID)
     * @param ttlSeconds Time to live in seconds
     * @return true if lock acquired, false otherwise
     */
    public boolean setIfAbsent(String key, String value, long ttlSeconds) {
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptText(LUA_SET_IF_ABSENT);
            script.setResultType(Long.class);

            Long result = stringRedisTemplate.execute(
                    script,
                    Collections.singletonList(key),
                    String.valueOf(ttlSeconds),
                    value
            );

            boolean acquired = result != null && result == 1L;

            if (acquired) {
                log.debug("🔒 Lock acquired: {} = {} (TTL: {}s)", key, value, ttlSeconds);
            } else {
                log.debug("🔒 Lock already held: {}", key);
            }

            return acquired;
        } catch (Exception e) {
            log.error("Error acquiring lock for key: {}", key, e);
            return false;
        }
    }

    /**
     * HIGH PRIORITY: Safe lock release - only delete if value matches
     * Prevents releasing someone else's lock
     *
     * @param key   Lock key
     * @param value Expected lock value
     * @return true if deleted, false if value didn't match or key didn't exist
     */
    public boolean deleteIfEquals(String key, String value) {
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>();
            script.setScriptText(LUA_DELETE_IF_EQUALS);
            script.setResultType(Long.class);

            Long result = stringRedisTemplate.execute(
                    script,
                    Collections.singletonList(key),
                    value
            );

            boolean deleted = result != null && result == 1L;

            if (deleted) {
                log.debug("🔓 Lock released: {}", key);
            } else {
                log.debug("🔓 Lock not released (value mismatch or not found): {}", key);
            }

            return deleted;
        } catch (Exception e) {
            log.error("Error releasing lock for key: {}", key, e);
            return false;
        }
    }

    /**
     * Standard set with expiry
     */
    public void setWithExpiry(String key, String value, long seconds) {
        try {
            redisTemplate.opsForValue().set(key, value, Duration.ofSeconds(seconds));
            log.debug("📝 Set with expiry: {} (TTL: {}s)", key, seconds);
        } catch (Exception e) {
            log.error("Error setting key with expiry: {}", key, e);
        }
    }

    /**
     * Check if key exists
     */
    public boolean exists(String key) {
        try {
            Boolean result = redisTemplate.hasKey(key);
            return result != null && result;
        } catch (Exception e) {
            log.error("Error checking existence of key: {}", key, e);
            return false;
        }
    }

    /**
     * Get string value
     */
    public String get(String key) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            return value != null ? value.toString() : null;
        } catch (Exception e) {
            log.error("Error getting key: {}", key, e);
            return null;
        }
    }

    /**
     * Delete key
     */
    public boolean delete(String key) {
        try {
            Boolean result = redisTemplate.delete(key);
            return result != null && result;
        } catch (Exception e) {
            log.error("Error deleting key: {}", key, e);
            return false;
        }
    }

    /**
     * Get automation cache
     */
    public AutomationCache getAutomationCache(String key) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value == null) return null;

            // Assuming value is stored as JSON string
            if (value instanceof String) {
                return objectMapper.readValue((String) value, AutomationCache.class);
            } else if (value instanceof AutomationCache) {
                return (AutomationCache) value;
            }

            return null;
        } catch (Exception e) {
            log.error("Error getting automation cache for key: {}", key, e);
            return null;
        }
    }

    /**
     * Set automation cache
     */
    public void setAutomationCache(String key, AutomationCache cache) {
        try {
            // Store as JSON for compatibility
            String json = objectMapper.writeValueAsString(cache);
            redisTemplate.opsForValue().set(key, json);
            log.debug("📝 Automation cache saved: {}", key);
        } catch (Exception e) {
            log.error("Error setting automation cache for key: {}", key, e);
        }
    }

    /**
     * Get recent device data
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getRecentDeviceData(String deviceId) {
        try {
            Object value = redisTemplate.opsForValue().get(deviceId);
            if (value == null) return null;

            if (value instanceof String) {
                return objectMapper.readValue((String) value, Map.class);
            } else if (value instanceof Map) {
                return (Map<String, Object>) value;
            }

            return null;
        } catch (Exception e) {
            log.error("Error getting device data for: {}", deviceId, e);
            return null;
        }
    }

    /**
     * Set recent device data
     */
    public void setRecentDeviceData(String key, Map<String, Object> data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            redisTemplate.opsForValue().set(key, json);
        } catch (Exception e) {
            log.error("Error setting device data for key: {}", key, e);
        }
    }

    /**
     * Increment counter atomically
     */
    public Long increment(String key) {
        try {
            return redisTemplate.opsForValue().increment(key);
        } catch (Exception e) {
            log.error("Error incrementing key: {}", key, e);
            return null;
        }
    }

    /**
     * Set with no expiry
     */
    public void set(String key, String value) {
        try {
            redisTemplate.opsForValue().set(key, value);
        } catch (Exception e) {
            log.error("Error setting key: {}", key, e);
        }
    }

    /**
     * Get remaining TTL for a key
     *
     * @return TTL in seconds, -1 if no expiry, -2 if key doesn't exist
     */
    public Long getTTL(String key) {
        try {
            return redisTemplate.getExpire(key, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Error getting TTL for key: {}", key, e);
            return -2L;
        }
    }

    /**
     * Extend TTL for an existing key
     */
    public boolean extendExpiry(String key, long additionalSeconds) {
        try {
            Long currentTTL = getTTL(key);
            if (currentTTL == null || currentTTL < 0) {
                return false;
            }

            return redisTemplate.expire(key, Duration.ofSeconds(currentTTL + additionalSeconds));
        } catch (Exception e) {
            log.error("Error extending expiry for key: {}", key, e);
            return false;
        }
    }
}
