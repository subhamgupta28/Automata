package dev.automata.automata.automation_engine;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Subscribes to the plan-invalidation pub/sub channel and evicts the local
 * JVM cache entry on every node when any node compiles/saves a new plan.
 * <p>
 * Without this listener, AutomationOrchestrator.updatePlan() /
 * invalidatePlan() publish messages that no one consumes — other nodes
 * keep serving stale plans out of PlanCache until the next 30-min
 * reconciler tick (or until they restart).
 * <p>
 * Registered against PLAN_INVALIDATE_CHANNEL via RedisListenerConfig.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlanInvalidationListener implements MessageListener {

    private final AutomationOrchestrator orchestrator;
    private final PlanCache planCache;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String automationId = new String(message.getBody(), StandardCharsets.UTF_8);
        boolean wasPresent = planCache.contains(automationId);
        orchestrator.evictLocalCaches(automationId);   // plan cache + name cache
        log.info("📥 [pubsub] Evicted '{}' from local caches (was cached: {})",
                automationId, wasPresent);
    }
}