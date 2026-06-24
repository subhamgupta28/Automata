package dev.automata.automata.v2;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Cluster-wide mutex so only one node performs a given reconcile action
 * (recompile) per cycle, even though every node runs @Scheduled reconcile()
 * independently.
 * <p>
 * Without this, N nodes all calling reconcile() every 30 min each detect the
 * same stale/missing plan and each recompile + write + publish — wasted CPU
 * on every node, and N redundant pub/sub invalidation messages per automation.
 * <p>
 * Lock TTL must comfortably exceed the time a single recompile() takes
 * (compile + 2 Redis writes + 1 publish — sub-second in practice). 20s is a
 * generous margin; if a node dies mid-recompile, the lock self-expires and
 * the next reconcile cycle (or another node, if cycles are staggered) retries.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReconcileLock {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String LOCK_PREFIX = "RECONCILE_LOCK:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(20);

    /**
     * Attempts to acquire the lock for this automation's recompile.
     *
     * @return true if this node won the lock and should proceed; false if
     * another node already holds it this cycle.
     */
    public boolean tryAcquire(String automationId) {
        String key = LOCK_PREFIX + automationId;
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(key, "locked", LOCK_TTL);
        return Boolean.TRUE.equals(acquired);
    }

    /**
     * Releases the lock early on success so a genuinely-needed re-recompile
     * (rare — e.g. another save lands seconds later) isn't blocked for the
     * full TTL. Safe to skip on failure paths; the TTL will clean it up.
     */
    public void release(String automationId) {
        redisTemplate.delete(LOCK_PREFIX + automationId);
    }
}
