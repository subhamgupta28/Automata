package dev.automata.automata.v2;

import dev.automata.automata.repository.ExecutionPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache of pre-compiled ExecutionPlans.
 * <p>
 * Each node keeps a local copy so evaluation never hits MongoDB or Redis
 * for the plan itself.  Invalidated when:
 * 1. An automation is saved (local invalidation via evict())
 * 2. Startup reconciliation re-loads all plans from MongoDB
 * <p>
 * In a multi-node deployment, plan invalidation is propagated via a Redis
 * pub/sub channel "automation:plan:invalidated" (see AutomationOrchestrator).
 * Each node subscribes and calls evict() on receipt.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanCache {

    private final ExecutionPlanRepository planRepository;
    private final AutomationStateStore stateStore;

    private final ConcurrentHashMap<String, ExecutionPlan> cache = new ConcurrentHashMap<>();


    // ─────────────────────────────────────────────────────────────────────
    // GET
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Get the plan for an automation.
     * Load order: local cache → Redis → MongoDB.
     * Returns null if the plan hasn't been compiled yet (automation pre-dates
     * the compiler — handled by startup reconciliation).
     */
    public ExecutionPlan get(String automationId) {
        ExecutionPlan local = cache.get(automationId);
        if (local != null) return local;

        // Try Redis
        ExecutionPlan fromRedis = stateStore.readPlan(automationId);
        if (fromRedis != null) {
            cache.put(automationId, fromRedis);
            return fromRedis;
        }

        // Try MongoDB
        return planRepository.findById(automationId)
                .map(plan -> {
                    cache.put(automationId, plan);
                    stateStore.writePlan(automationId, plan);
                    return plan;
                })
                .orElse(null);
    }


    // ─────────────────────────────────────────────────────────────────────
    // PUT / EVICT
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Store a newly compiled plan in all layers.
     * Called from AutomationOrchestrator after a save.
     */
    public void put(String automationId, ExecutionPlan plan) {
        cache.put(automationId, plan);
        stateStore.writePlan(automationId, plan);
        planRepository.save(plan);
        log.debug("📥 Plan cached for automation '{}'", automationId);
    }

    /**
     * Evict from local cache only.
     * Called on receipt of a "plan invalidated" pub/sub message from another node,
     * or before a delete.  Next get() will reload from Redis/MongoDB.
     */
    public void evict(String automationId) {
        cache.remove(automationId);
        log.debug("🗑️ Plan evicted from local cache for automation '{}'", automationId);
    }

    /**
     * Full delete — removes from local cache, Redis, and MongoDB.
     * Called from deleteAutomation().
     */
    public void delete(String automationId) {
        cache.remove(automationId);
        stateStore.deletePlan(automationId);
        planRepository.deleteById(automationId);
        log.info("🧹 Plan deleted for automation '{}'", automationId);
    }

    /**
     * Warm the local cache from MongoDB on startup.
     * Called by AutomationOrchestrator @PostConstruct reconciliation.
     */
    public void warmAll() {
        int count = 0;
        for (ExecutionPlan plan : planRepository.findAll()) {
            if (plan.getSchemaVersion() < ExecutionPlan.CURRENT_SCHEMA_VERSION) {
                log.warn("⚠️ Plan '{}' is schema v{} (current=v{}) — skipping until recompiled",
                        plan.getAutomationName(), plan.getSchemaVersion(),
                        ExecutionPlan.CURRENT_SCHEMA_VERSION);
                continue;
            }
            cache.put(plan.getAutomationId(), plan);
            stateStore.writePlan(plan.getAutomationId(), plan);
            count++;
        }
        log.info("🔥 Plan cache warmed: {} plans loaded", count);
    }
}