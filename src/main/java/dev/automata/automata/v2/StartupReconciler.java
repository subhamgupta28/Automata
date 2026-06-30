package dev.automata.automata.v2;

import dev.automata.automata.dto.AutomationRuntimeState;
import dev.automata.automata.model.Automation;
import dev.automata.automata.repository.AutomationRepository;
import dev.automata.automata.service.MainService;
import dev.automata.automata.service.RedisService;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Closes the gap between "the app just restarted" and "the next real device
 * event or scheduled tick happens to arrive" for automations that were
 * ACTIVE at shutdown.
 * <p>
 * The problem this solves
 * ────────────────────────
 * AutomationRuntimeState (nodeStates, topLevelState, conditionMemories)
 * survives a restart in Redis — but restarting never triggers a
 * re-evaluation of anything. The first time an automation gets evaluated
 * again is whenever its trigger device next reports new data, or — only if
 * it has a "scheduled" condition node somewhere — the next 12s periodic
 * tick. A purely data-driven automation (no scheduled node at all) stays
 * completely dormant after restart until its trigger device's next
 * real-world data event, however long that takes. If the underlying
 * condition changed state while the app was down, nothing notices and
 * nothing reverts/re-applies the corresponding actions until that
 * unbounded gap closes on its own.
 * <p>
 * What this does
 * ───────────────
 * Once on every application startup (after the full Spring context — and
 * therefore every bean this depends on — is up), for every enabled
 * automation whose PERSISTED state shows it was active before shutdown
 * (topLevelState=ACTIVE, or any stateful condition-tree node ACTIVE/HOLDING),
 * this forces ONE immediate re-evaluation against the best currently
 * available real data, by calling AutomationOrchestrator.execute() — the
 * exact same public entry point every other trigger path already uses.
 * <p>
 * Deliberately NOT a separate evaluation path: there is no
 * "startup mode" branch added anywhere inside AutomationOrchestrator or
 * AutomationEvaluator. Every existing guard (snooze, timed-disable,
 * coalition, CAS, the stranded-descendant negative-action fold-in, duration
 * window arming, precise per-node state transitions) applies identically,
 * with zero risk of a parallel "reconciliation path" drifting out of sync
 * with normal evaluation logic over time.
 * <p>
 * Safety properties
 * ───────────────────
 * - Only automations that were genuinely ACTIVE are touched — idle
 * automations have nothing to reconcile and are skipped without paying
 * for a full evaluation.
 * - Uses the SAME ReconcileLock the periodic reconcile() job already uses,
 * per automationId, so a multi-node rolling restart doesn't have every
 * instance independently force-evaluate the same automations at once.
 * - Staggered dispatch via a dedicated single-thread ScheduledExecutorService
 * — NOT the shared automationExecutor pool. AutomationOrchestrator.execute()
 * is itself @Async("automationExecutor"), so blocking a thread FROM that
 * same pool to stagger dispatch (e.g. via Thread.sleep in an @Async loop)
 * would self-starve real automation traffic for the duration of the
 * reconciliation pass — exactly the kind of pool contention this design
 * is trying to avoid. The coordinating loop here runs entirely off-pool;
 * only the actual orchestrator.execute() calls it triggers land on
 * automationExecutor, exactly like any other real trigger would.
 * - Concurrent races with a genuine real trigger arriving at the same time
 * are handled by the EXISTING CAS-with-retry protocol in
 * AutomationOrchestrator — no new concurrency mechanism needed here.
 * - Never blocks application startup: ApplicationReadyEvent fires after
 * the embedded server is already serving traffic, and this listener
 * itself only schedules work on its own dedicated executor before
 * returning immediately.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupReconciler {

    private final AutomationRepository automationRepository;
    private final AutomationStateStore stateStore;
    private final AutomationOrchestrator orchestrator;
    private final PlanCache planCache;
    private final ReconcileLock reconcileLock;
    private final RedisService redisService;
    private final MainService mainService;

    private static final String RECONCILE_USER = "system-startup-reconcile";

    /**
     * Small delay between each automation's reconciliation dispatch, to
     * avoid spiking Redis/MongoDB/device traffic all at the same instant on
     * a restart. Deliberately conservative — this runs once per process
     * lifetime, so there's no cost to taking it slowly.
     */
    private static final long STAGGER_DELAY_MS = 250L;

    /**
     * Dedicated single-thread scheduler for the reconciliation pass —
     * intentionally NOT the shared automationExecutor pool (see class
     * javadoc). Single daemon thread is enough: the loop body itself is
     * cheap (a few Redis reads and a queued dispatch per automation), all
     * the real work happens asynchronously on automationExecutor once
     * orchestrator.execute() is called.
     */
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "startup-reconciler");
                t.setDaemon(true);
                return t;
            });

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        scheduler.submit(this::reconcileActiveAutomations);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }

    void reconcileActiveAutomations() {
        List<Automation> enabled;
        try {
            enabled = automationRepository.findEnabledForExecution();
        } catch (Exception e) {
            log.error("❌ [startup-reconcile] Failed to load enabled automations: {}", e.getMessage(), e);
            return;
        }

        if (enabled.isEmpty()) {
            log.info("🟢 [startup-reconcile] No enabled automations — nothing to reconcile.");
            return;
        }

        AtomicInteger candidates = new AtomicInteger();
        AtomicInteger skippedLocked = new AtomicInteger();
        AtomicInteger skippedNoData = new AtomicInteger();
        AtomicInteger dispatched = new AtomicInteger();

        // Staggered scheduling: each candidate's actual reconciliation work
        // is submitted to run `delaySlot * STAGGER_DELAY_MS` from now, on
        // THIS dedicated scheduler thread (cheap — Redis reads + a queued
        // dispatch), never blocking it with Thread.sleep. The heavy lifting
        // (the evaluation itself) happens on automationExecutor via the
        // normal execute() call, exactly like any other real trigger.
        long delaySlot = 0;
        for (Automation automation : enabled) {
            String id = automation.getId();

            AutomationRuntimeState state;
            try {
                state = stateStore.read(id);
            } catch (Exception e) {
                log.warn("⚠️ [startup-reconcile] Failed to read state for '{}': {} — skipping",
                        automation.getName(), e.getMessage());
                continue;
            }

            if (!wasActiveBeforeShutdown(state)) {
                continue; // nothing to reconcile — idle automations are unaffected
            }
            candidates.incrementAndGet();

            long thisDelayMs = delaySlot * STAGGER_DELAY_MS;
            delaySlot++;

            scheduler.schedule(() ->
                            reconcileOne(automation, dispatched, skippedLocked, skippedNoData),
                    thisDelayMs, TimeUnit.MILLISECONDS);
        }

        long totalDelayMs = delaySlot * STAGGER_DELAY_MS;
        scheduler.schedule(() ->
                        log.info("✅ [startup-reconcile] Done — {} enabled, {} were active before shutdown, "
                                        + "{} dispatched, {} skipped (locked elsewhere), {} skipped (no data available).",
                                enabled.size(), candidates.get(), dispatched.get(),
                                skippedLocked.get(), skippedNoData.get()),
                totalDelayMs + STAGGER_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Reconciles a single automation — acquires the lock, resolves a
     * payload, dispatches via the normal orchestrator.execute() entry
     * point, releases the lock. Runs on the dedicated scheduler thread;
     * the only blocking-ish work here is a couple of Redis/Mongo reads,
     * never the evaluation itself (that's async on automationExecutor).
     */
    private void reconcileOne(Automation automation,
                              AtomicInteger dispatched,
                              AtomicInteger skippedLocked,
                              AtomicInteger skippedNoData) {
        String id = automation.getId();

        if (!reconcileLock.tryAcquire(id)) {
            // Another cluster node is already reconciling (or recompiling)
            // this automation — don't duplicate the work.
            skippedLocked.incrementAndGet();
            return;
        }

        try {
            Map<String, Object> payload = resolveBestAvailablePayload(automation);
            if (payload == null) {
                skippedNoData.incrementAndGet();
                log.warn("⚠️ [startup-reconcile] '{}' was active before shutdown but no "
                                + "Redis or DB data is available for trigger device '{}' — "
                                + "skipping; it will reconcile naturally on its next real event.",
                        automation.getName(), automation.getTrigger().getDeviceId());
                return;
            }

            String homeId = resolveHomeId(automation, id);
            orchestrator.execute(id, payload, RECONCILE_USER,
                    automation.getTrigger().getDeviceId(), homeId);
            dispatched.incrementAndGet();

            log.info("🔁 [startup-reconcile] '{}' was active before shutdown — "
                    + "forced re-evaluation dispatched.", automation.getName());

        } catch (Exception e) {
            log.error("❌ [startup-reconcile] Failed to reconcile '{}': {}",
                    automation.getName(), e.getMessage(), e);
        } finally {
            // execute() is itself @Async — the lock only needs to protect
            // against duplicate DISPATCH, not duplicate completion. Release
            // immediately after dispatching, mirroring how
            // AutomationOrchestrator.recompileIfNeeded() already releases
            // its own reconcileLock right after its (synchronous) recompile
            // call, not after some later async step.
            reconcileLock.release(id);
        }
    }


    // ─────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────

    /**
     * True when the persisted state indicates this automation had something
     * active at the moment it was last written — covering both the
     * legacy/no-condition-tree path (topLevelState) and the unified
     * condition-tree path (any individual stateful node ACTIVE/HOLDING).
     */
    private boolean wasActiveBeforeShutdown(AutomationRuntimeState state) {
        if (state.isTopLevelActive()) return true;
        if (state.getNodeStates() == null) return false;
        return state.getNodeStates().values().stream()
                .anyMatch(s -> "ACTIVE".equals(s) || "HOLDING".equals(s));
    }

    /**
     * Resolves the best currently-available payload for the automation's
     * PRIMARY trigger device: Redis recent data first (fast, likely fresh
     * if the device has reported since restart), falling back to the last
     * known MongoDB record (mirrors AutomationService.buildDbFallbackPayload()
     * exactly, so "last_seen" semantics stay consistent with every other
     * DB-fallback path in the codebase).
     * <p>
     * Deliberately more willing to fall back to DB than the periodic
     * scheduler's triggerPeriodicAutomations() is: that job runs every 12s
     * forever and is conservative about not wasting cycles on guesswork;
     * this runs exactly once per process lifetime, so it's worth trying
     * harder to find SOME real data rather than skipping reconciliation
     * outright.
     * <p>
     * Returns null only when NEITHER Redis nor MongoDB has anything for the
     * trigger device at all — in that case there is genuinely nothing to
     * evaluate against, and the automation is left to reconcile naturally
     * whenever its trigger device eventually reports.
     */
    private Map<String, Object> resolveBestAvailablePayload(Automation automation) {
        String deviceId = automation.getTrigger().getDeviceId();
        if (deviceId == null || deviceId.isBlank()) return null;

        try {
            Map<String, Object> recent = redisService.getRecentDeviceData(deviceId);
            if (recent != null && !recent.isEmpty()) {
                return recent;
            }
        } catch (Exception e) {
            log.warn("⚠️ [startup-reconcile] Redis lookup failed for device '{}': {}",
                    deviceId, e.getMessage());
        }

        try {
            var record = mainService.getLastFullData(deviceId);
            if (record == null) return null;

            Map<String, Object> payload = new HashMap<>();
            if (record.getData() != null) payload.putAll(record.getData());
            long lastSeenMs = record.getUpdateDate() != null
                    ? record.getUpdateDate().getEpochSecond() * 1000L : 0L;
            payload.put("last_seen", lastSeenMs);
            return payload.isEmpty() ? null : payload;
        } catch (Exception e) {
            log.warn("⚠️ [startup-reconcile] DB fallback failed for device '{}': {}",
                    deviceId, e.getMessage());
            return null;
        }
    }

    /**
     * Resolves homeId for the orchestrator.execute() call — prefers the
     * compiled plan's homeId (already resolved/validated at compile time),
     * falling back to the raw Automation record if the plan isn't cached
     * yet (PlanCache may still be cold this early — see PlanCache.warmAll()
     * being dead code, confirmed separately; executeInternal()'s own lazy
     * fallback handles loading the plan regardless, this is purely for the
     * homeId argument).
     */
    private String resolveHomeId(Automation automation, String automationId) {
        ExecutionPlan plan = planCache.get(automationId);
        if (plan != null && plan.getHomeId() != null) return plan.getHomeId();
        return automation.getHomeId();
    }
}