package dev.automata.automata.v2;

import dev.automata.automata.repository.AutomationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process JVM cache for compiled ExecutionPlans.
 * <p>
 * Why an in-process cache instead of only Redis?
 * ExecutionPlans are compiled once at save time and are immutable until the
 * automation is re-saved.  Reading from Redis on every 12s tick (per
 * automation) adds unnecessary network latency and deserialization cost.
 * The JVM map gives O(1) access with zero IO.
 * <p>
 * Consistency model:
 * - put()  : writes to JVM map (called by orchestrator after compile)
 * - evict(): removes from JVM map (called on plan invalidation pub/sub)
 * - delete(): removes from JVM map (called by AutomationService.deleteAutomation)
 * - warmAll(): populates from Redis on startup (fallback for plans that
 * were compiled before this node started)
 * <p>
 * Cross-node invalidation:
 * AutomationOrchestrator subscribes to the Redis pub/sub channel
 * "automation:plan:invalidated" and calls evict() on every message so
 * all cluster nodes drop stale plans within milliseconds of a save.
 * <p>
 * Memory:
 * Each ExecutionPlan is a small POJO (~2-10 KB depending on action count).
 * Thousands of automations would still fit comfortably in the JVM heap.
 * No eviction policy is needed — the map is bounded by the number of
 * automations in the system.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanCache {

    private final AutomationStateStore stateStore;
    private final AutomationRepository automationRepository;

    // ConcurrentHashMap for lock-free reads on the hot path
    private final ConcurrentHashMap<String, ExecutionPlan> cache = new ConcurrentHashMap<>();


    // ─────────────────────────────────────────────────────────────────────
    // CORE OPERATIONS
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Returns the compiled plan for the given automation ID, or null if not cached.
     * Called on every evaluation tick — must be O(1) and allocation-free.
     */
    public ExecutionPlan get(String automationId) {
        return cache.get(automationId);
    }

    /**
     * Stores a compiled plan.
     * Called by AutomationOrchestrator.updatePlan() after a successful compile.
     * Also called by warmAll() on startup.
     */
    public void put(String automationId, ExecutionPlan plan) {
        cache.put(automationId, plan);
        log.debug("📦 PlanCache.put '{}' (v{})", automationId, plan.getSchemaVersion());
    }

    /**
     * Removes a plan from the JVM cache.
     * Called when the orchestrator receives a pub/sub invalidation event so
     * the next evaluation re-reads from Redis (via stateStore.readPlan())
     * or triggers a recompile if the plan is missing from Redis too.
     */
    public void evict(String automationId) {
        ExecutionPlan removed = cache.remove(automationId);
        if (removed != null)
            log.debug("🗑️ PlanCache.evict '{}'", automationId);
    }

    /**
     * Removes the plan and also deletes it from Redis (AutomationStateStore).
     * Called by AutomationService.deleteAutomation() for a full cleanup.
     */
    public void delete(String automationId) {
        cache.remove(automationId);
        stateStore.deletePlan(automationId);
        log.info("🗑️ PlanCache.delete '{}' (JVM + Redis)", automationId);
    }


    // ─────────────────────────────────────────────────────────────────────
    // STARTUP WARM
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Populates the JVM cache from Redis on startup.
     * <p>
     * Strategy:
     * 1. For every enabled automation in MongoDB, try to load its plan from
     * Redis (AutomationStateStore.readPlan).
     * 2. If Redis has the plan and the schema version matches current, cache it.
     * 3. If Redis is missing the plan (or schema version is stale), log a
     * warning — the orchestrator will log a separate warning asking the
     * operator to re-save in the editor.
     * <p>
     * This is called once from AutomationOrchestrator.@PostConstruct reconcile().
     * It runs synchronously on the main thread during startup — acceptable
     * because it is bounded by the number of automations (typically < 1000)
     * and each stateStore.readPlan() is a single Redis GET.
     */
    public void warmAll() {
        int loaded = 0, missing = 0, stale = 0;

        for (var automation : automationRepository.findEnabledForExecution()) {
            String id = automation.getId();
            try {
                ExecutionPlan plan = stateStore.readPlan(id);
                if (plan == null) {
                    missing++;
                    log.warn("⚠️ PlanCache.warmAll: no Redis plan for '{}' (id={})",
                            automation.getName(), id);
                    continue;
                }
                if (plan.getSchemaVersion() < ExecutionPlan.CURRENT_SCHEMA_VERSION) {
                    stale++;
                    log.warn("⚠️ PlanCache.warmAll: stale schema v{} for '{}' (current=v{}) — re-save to upgrade",
                            plan.getSchemaVersion(), automation.getName(),
                            ExecutionPlan.CURRENT_SCHEMA_VERSION);
                    // Still cache the stale plan — it will work with the fields it has.
                    // A re-save will upgrade it.
                    cache.put(id, plan);
                    continue;
                }
                cache.put(id, plan);
                loaded++;
            } catch (Exception e) {
                log.error("❌ PlanCache.warmAll: failed to load '{}': {}", id, e.getMessage());
            }
        }

        log.info("🔥 PlanCache warmed: {} loaded, {} missing, {} stale schema",
                loaded, missing, stale);
    }


    // ─────────────────────────────────────────────────────────────────────
    // INTROSPECTION
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Returns an unmodifiable view of all cached plans.
     * Used by AutomationOrchestrator.reconcile() to prefill the name cache
     * without additional MongoDB round-trips.
     */
    public Map<String, ExecutionPlan> getAllPlans() {
        return Collections.unmodifiableMap(cache);
    }

    /**
     * Returns the number of plans currently in the JVM cache.
     * Used by reconcile() for startup logging.
     */
    public int size() {
        return cache.size();
    }

    /**
     * Returns true if the cache contains a plan for the given automation.
     */
    public boolean contains(String automationId) {
        return cache.containsKey(automationId);
    }

    /**
     * Clears all cached plans.
     * Intended for testing or emergency cache flush only.
     * In production, prefer evict() per automation to avoid evaluation gaps.
     */
    public void clear() {
        int count = cache.size();
        cache.clear();
        log.warn("⚠️ PlanCache cleared ({} plans evicted)", count);
    }
}