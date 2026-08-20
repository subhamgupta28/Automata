package dev.automata.automata.automation_engine;

import dev.automata.automata.model.Automation;
import dev.automata.automata.repository.AutomationRepository;
import dev.automata.automata.utils.Feature;
import dev.automata.automata.utils.FeatureEnabled;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlanReconciliationService {

    private final PlanCache planCache;
    private final AutomationStateStore stateStore;
    private final AutomationRepository automationRepository;
    private final ExecutionPlanCompiler planCompiler;
    private final ReconcileLock reconcileLock;
    private final RedisTemplate<String, String> redisTemplate;

    // ── SCHEDULED RECONCILER (moved verbatim from AutomationOrchestrator) ──

    @Scheduled(fixedDelay = 4 * 60 * 60 * 1_000)
    @FeatureEnabled(value = Feature.PERIODIC_AUTOMATION_SERVICE)
    public void reconcile() {
        List<Automation> enabled = automationRepository.findEnabledForExecution();
        if (enabled.isEmpty()) return;

        AtomicInteger recompiled = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();

        for (Automation automation : enabled) {
            try {
                recompileIfNeeded(automation, recompiled);
            } catch (Exception e) {
                failed.incrementAndGet();
                log.error("❌ [reconciler] Failed for '{}' ({}): {}",
                        automation.getName(), automation.getId(), e.getMessage(), e);
            }
        }

        if (recompiled.get() > 0 || failed.get() > 0) {
            log.info("🔄 [reconciler] Done — {}/{} recompiled, {} failed",
                    recompiled.get(), enabled.size(), failed.get());
        } else {
            log.debug("✅ [reconciler] All {} plans are fresh", enabled.size());
        }
    }

    private void recompileIfNeeded(Automation automation, AtomicInteger counter) {
        String id = automation.getId();
        String name = automation.getName();

        ExecutionPlan cached = planCache.get(id);

        if (cached == null) {
            if (!reconcileLock.tryAcquire(id)) {
                log.debug("🔒 [reconciler] '{}' missing but another node already recompiling", name);
                return;
            }
            log.warn("⚠️ [reconciler] Plan missing for '{}' — recompiling", name);
            try {
                recompile(automation, "missing from cache");
                counter.incrementAndGet();
            } finally {
                reconcileLock.release(id);
            }
            return;
        }

        Instant updatedAt = automation.getUpdateDate();
        Instant compiledAt = cached.getCompiledAt();

        if (isStale(updatedAt, compiledAt)) {
            if (!reconcileLock.tryAcquire(id)) {
                log.debug("🔒 [reconciler] '{}' stale but another node already recompiling", name);
                return;
            }
            log.warn("⚠️ [reconciler] Plan stale for '{}' (DB={}, plan={}) — recompiling",
                    name, updatedAt, compiledAt);
            try {
                recompile(automation, "stale (DB newer than cache)");
                counter.incrementAndGet();
            } finally {
                reconcileLock.release(id);
            }
        }
    }

    private void recompile(Automation automation, String reason) {
        ExecutionPlan plan = planCompiler.compile(automation);
        planCache.put(automation.getId(), plan);
        stateStore.writePlan(automation.getId(), plan);
        redisTemplate.convertAndSend(AutomationOrchestrator.PLAN_INVALIDATE_CHANNEL, automation.getId());
        log.info("✅ [reconciler] '{}' recompiled — reason: {}", automation.getName(), reason);
    }

    private boolean isStale(Instant updatedAt, Instant compiledAt) {
        if (updatedAt == null || compiledAt == null) return true;
        return updatedAt.isAfter(compiledAt.plusSeconds(5));
    }

    // ── ON-DEMAND (was executeInternal() Steps 1 / 1b) ─────────────────

    /**
     * Returns the freshest plan for automationId, recompiling from DB if
     * necessary. Returns empty when the caller should skip this
     * execution entirely (automation missing/disabled, or recompile
     * failed) — already logged internally, caller doesn't need to log again.
     */
    public Optional<ExecutionPlan> ensureFresh(String automationId, String traceId) {
        ExecutionPlan plan = planCache.get(automationId);

        if (plan == null) {
            plan = stateStore.readPlan(automationId);
            if (plan != null) {
                planCache.put(automationId, plan);
                log.info("♻️ [traceId={}] '{}' warmed from Redis", traceId, automationId);
            }
        } else {
            long remoteVersion = stateStore.readPlanVersion(automationId);
            long localVersion = plan.getCompiledAt() != null ? plan.getCompiledAt().getEpochSecond() : 0L;
            if (remoteVersion > 0 && remoteVersion != localVersion) {
                log.info("♻️ [traceId={}] '{}' local plan stale — refreshing from Redis", traceId, automationId);
                ExecutionPlan fresh = stateStore.readPlan(automationId);
                if (fresh != null) {
                    planCache.put(automationId, fresh);
                    plan = fresh;
                }
            }
        }

        if (plan != null) return Optional.of(plan);

        if (reconcileLock.tryAcquire(automationId)) {
            try {
                log.warn("⚠️ [traceId={}] Plan for '{}' missing from both JVM and Redis — recompiling from DB",
                        traceId, automationId);
                Automation automation = automationRepository.findById(automationId).orElse(null);
                if (automation == null || !automation.getIsEnabled()) {
                    log.warn("⏭️ [traceId={}] Automation '{}' not found or disabled — skipping.", traceId, automationId);
                    return Optional.empty();
                }
                ExecutionPlan compiled = planCompiler.compile(automation);
                planCache.put(automationId, compiled);
                stateStore.writePlan(automationId, compiled);
                log.info("✅ [traceId={}] '{}' recompiled on-demand and cached", traceId, automationId);
                return Optional.of(compiled);
            } catch (Exception e) {
                log.error("❌ [traceId={}] On-demand recompile failed for '{}': {}", traceId, automationId, e.getMessage(), e);
                return Optional.empty();
            } finally {
                reconcileLock.release(automationId);
            }
        }

        log.debug("🔒 [traceId={}] '{}' recompile in progress on another node — retrying Redis", traceId, automationId);
        try {
            Thread.sleep(300);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        ExecutionPlan retried = stateStore.readPlan(automationId);
        if (retried != null) {
            planCache.put(automationId, retried);
            return Optional.of(retried);
        }
        log.warn("⏭️ [traceId={}] Still no plan for '{}' after lock wait — skipping.", traceId, automationId);
        return Optional.empty();
    }
}