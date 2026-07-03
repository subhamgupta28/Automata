package dev.automata.automata.v2;

import dev.automata.automata.dto.AutomationRuntimeState;
import dev.automata.automata.dto.ConditionMemory;
import dev.automata.automata.model.Automation;
import dev.automata.automata.model.AutomationLog;
import dev.automata.automata.repository.AutomationRepository;
import dev.automata.automata.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Core automation orchestrator.
 *
 * <p>Bug fixes (this version)
 * ─────────────────────────
 * BUG 4 — OR fanout branch actions silently swallowed when top-level ACTIVE
 * <p>
 * dispatchResult() and computeNextState() now handle the new
 * EvalOutcome.BRANCH_TRIGGERED outcome emitted by the evaluator when an OR
 * fanout branch node transitions inactive→active while the top-level
 * automation is already ACTIVE.
 * <p>
 * Key differences vs TRIGGERED:
 * • BRANCH_TRIGGERED dispatches the branch's own per-node positiveActions,
 * NOT plan.getTopLevelPositiveActions(). The branch-level actions are
 * already in result.getActionsToFire() from the evaluator.
 * • computeNextState() does NOT reset/overwrite topLevelState — it was
 * already ACTIVE and must remain so. Only the per-node nodeStates are
 * updated (via the shared applyPerNodeActiveFlags() call).
 * • publishLog() maps BRANCH_TRIGGERED → LogStatus.TRIGGERED so the log
 * stream is unchanged for consumers.
 * • hasChanges() in EvalResult returns true for BRANCH_TRIGGERED so the
 * orchestrator's early-return guard does not suppress dispatch.
 * <p>
 * Previously documented bug fixes (BUG 1–3, carried forward unchanged):
 * BUG 1 — Stranded descendants now fire their negative actions
 * BUG 2 — durationMinutes now actually holds an interval condition true
 * BUG 3 — computeNextState() no longer blindly marks the whole tree ACTIVE
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutomationOrchestrator {

    private final PlanCache planCache;
    private final AutomationStateStore stateStore;
    private final AutomationEvaluator evaluator;
    private final ActionDispatcher dispatcher;
    private final AutomationLogStream logStream;
    private final AutomationRepository automationRepository;
    private final NotificationService notificationService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ExecutionPlanCompiler planCompiler;
    private final AutomationLivePublisher livePublisher;
    private final ReconcileLock reconcileLock;
    private final CoalitionGuard coalitionGuard;

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int CAS_MAX_RETRIES = 2;
    public static final String PLAN_INVALIDATE_CHANNEL = "automation:plan:invalidated";

    private final ConcurrentHashMap<String, String> nameCache = new ConcurrentHashMap<>();


    // ─────────────────────────────────────────────────────────────────────
    // RECONCILER
    // ─────────────────────────────────────────────────────────────────────

    @Scheduled(fixedDelay = 4 * 60 * 60 * 1_000)
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

        Date updatedAt = automation.getUpdateDate();
        Date compiledAt = cached.getCompiledAt();

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
        updatePlan(automation.getId(), plan);
        stateStore.writePlan(automation.getId(), plan);
        log.info("✅ [reconciler] '{}' recompiled — reason: {}", automation.getName(), reason);
    }

    private boolean isStale(Date updatedAt, Date compiledAt) {
        if (updatedAt == null || compiledAt == null) return true;
        return updatedAt.getTime() > compiledAt.getTime() + 2_000;
    }


    // ─────────────────────────────────────────────────────────────────────
    // MAIN ENTRY POINTS
    // ─────────────────────────────────────────────────────────────────────

    @Async("automationExecutor")
    public void execute(String automationId, Map<String, Object> payload, String user) {
        ExecutionPlan plan = planCache.get(automationId);
        String firingDeviceId = plan != null ? plan.getTriggerDeviceId() : null;
        executeInternal(automationId, payload, user, firingDeviceId,
                plan != null ? plan.getHomeId() : null);
    }

    @Async("automationExecutor")
    public void execute(String automationId, Map<String, Object> payload,
                        String user, String firingDeviceId, String homeId) {
        executeInternal(automationId, payload, user, firingDeviceId, homeId);
    }

    private void executeInternal(String automationId, Map<String, Object> payload,
                                 String user, String firingDeviceId, String homeId) {

        String traceId = automationId.substring(0, Math.min(8, automationId.length()))
                + "-" + System.currentTimeMillis()
                + "-" + Long.toHexString(ThreadLocalRandom.current().nextLong() & 0xFFFFFFFFL);

        log.debug("🔍 [traceId={}] execute — automation='{}' firingDevice='{}'",
                traceId, automationId, firingDeviceId);

        // ── 1. Load plan ──────────────────────────────────────────────────
        ExecutionPlan plan = planCache.get(automationId);
        if (plan == null) {
            plan = stateStore.readPlan(automationId);
            if (plan != null) {
                planCache.put(automationId, plan);
                log.info("♻️ [traceId={}] '{}' warmed from Redis", traceId, automationId);
            }
        } else {
            long remoteVersion = stateStore.readPlanVersion(automationId);
            long localVersion = plan.getCompiledAt() != null ? plan.getCompiledAt().getTime() : 0L;
            if (remoteVersion > 0 && remoteVersion != localVersion) {
                log.info("♻️ [traceId={}] '{}' local plan stale — refreshing from Redis",
                        traceId, automationId);
                ExecutionPlan fresh = stateStore.readPlan(automationId);
                if (fresh != null) {
                    planCache.put(automationId, fresh);
                    plan = fresh;
                }
            }
        }
        // ── 1b. Redis miss — recompile from DB ───────────────────────────
        if (plan == null) {
            if (reconcileLock.tryAcquire(automationId)) {
                try {
                    log.warn("⚠️ [traceId={}] Plan for '{}' missing from both JVM and Redis — recompiling from DB",
                            traceId, automationId);
                    Automation automation = automationRepository.findById(automationId).orElse(null);
                    if (automation == null || !automation.getIsEnabled()) {
                        log.warn("⏭️ [traceId={}] Automation '{}' not found or disabled — skipping.",
                                traceId, automationId);
                        return;
                    }
                    plan = planCompiler.compile(automation);
                    planCache.put(automationId, plan);
                    stateStore.writePlan(automationId, plan);
                    log.info("✅ [traceId={}] '{}' recompiled on-demand and cached", traceId, automationId);
                } catch (Exception e) {
                    log.error("❌ [traceId={}] On-demand recompile failed for '{}': {}",
                            traceId, automationId, e.getMessage(), e);
                    return;
                } finally {
                    reconcileLock.release(automationId);
                }
            } else {
                // Another node is already recompiling — wait briefly and retry from Redis
                log.debug("🔒 [traceId={}] '{}' recompile in progress on another node — retrying Redis",
                        traceId, automationId);
                try {
                    Thread.sleep(300);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                plan = stateStore.readPlan(automationId);
                if (plan != null) {
                    planCache.put(automationId, plan);
                } else {
                    log.warn("⏭️ [traceId={}] Still no plan for '{}' after lock wait — skipping.",
                            traceId, automationId);
                    return;
                }
            }
        }

        // ── 2. Snooze / timed-disable ─────────────────────────────────────
        if (stateStore.isSnoozed(automationId)) {
            long rem = Optional.ofNullable(stateStore.snoozeTTL(automationId)).orElse(0L);
            publishSkippedLog(automationId, plan, user, payload,
                    "Snoozed — " + rem / 60 + "min remaining", traceId);
            return;
        }
        if (stateStore.isTimedDisabled(automationId)) {
            long rem = Optional.ofNullable(stateStore.timedDisableTTL(automationId)).orElse(0L);
            publishSkippedLog(automationId, plan, user, payload,
                    "Timed-disabled — " + rem / 60 + "min remaining", traceId);
            return;
        }

        // ── 3. Read state ─────────────────────────────────────────────────
        AutomationRuntimeState state = stateStore.read(automationId);

        // ── 4. Coalition guard ────────────────────────────────────────────
        if (plan.hasCoalition() && firingDeviceId != null) {
            long nowMs = System.currentTimeMillis();
            AutomationRuntimeState stateWithMember = state.withNextVersion();
            stateWithMember.recordMemberFired(firingDeviceId, nowMs);

            CoalitionGuard.CoalitionResult coalitionResult =
                    coalitionGuard.evaluate(plan.getTriggerCoalition(),
                            firingDeviceId, stateWithMember, nowMs);

            log.debug("🤝 [{}] Coalition: {} — {}", plan.getAutomationName(),
                    coalitionResult.status(), coalitionResult.reason());

            if (!coalitionResult.shouldProceed()) {
                stateStore.forceWrite(automationId, stateWithMember);
                publishSkippedLog(automationId, plan, user, payload,
                        "Coalition " + coalitionResult.status() + ": " + coalitionResult.reason(),
                        traceId);
                return;
            }

            state = stateWithMember;

            if (plan.getTriggerCoalition().getMode() == TriggerCoalition.CoalitionMode.SEQUENCE) {
                handleSequenceProgress(plan.getTriggerCoalition(), firingDeviceId, state,
                        coalitionResult.status(), nowMs);
            }
        }

        // ── 5. Evaluate ───────────────────────────────────────────────────
        AutomationEvaluator.EvalResult result;
        try {
            result = evaluator.evaluate(plan, payload, state, automationId, traceId);
            log.debug("Automation {} final result getConditionResults={} getActionsToFire={} outcome={}",
                    plan.getAutomationName(),
                    result.getConditionResults(),
                    result.getActionsToFire(),
                    result.getOutcome());
        } catch (Exception e) {
            log.error("❌ [traceId={}] Evaluation failed: {}", traceId, e.getMessage(), e);
            publishSkippedLog(automationId, plan, user, payload,
                    "Evaluation error: " + e.getMessage(), traceId);
            return;
        }

        // BUG 1 fix: fold in negative actions for stranded descendants
        if (result.getOutcome() == AutomationEvaluator.EvalOutcome.C1_NEGATIVE) {
            result = foldInStrandedNegativeActions(result, plan, state, automationId);
        }

        log.debug("📋 [traceId={}] outcome={} c1={} anyWasActive={}",
                traceId, result.getOutcome(), result.isC1True(), result.isAnyWasActive());

        // ── 6. No state change — persist memory + snapshot ────────────────
        if (!result.hasChanges()) {
            writePostCasMemoryUpdates(automationId, result);
            writeEvalSnapshot(automationId, plan, result, state);
            livePublisher.publish(plan, result, state, payload);
            publishLog(automationId, plan, user, payload, result);
            return;
        }

        // ── 7. Compute next state ─────────────────────────────────────────
        AutomationRuntimeState nextState = computeNextState(state, result, plan);

        // ── 8. CAS write with retry ───────────────────────────────────────
        boolean written = false;
        for (int attempt = 0; attempt < CAS_MAX_RETRIES && !written; attempt++) {
            if (attempt > 0) {
                log.debug("🔄 [traceId={}] CAS retry attempt {}", traceId, attempt);
                try {
                    state = stateStore.read(automationId);
                    result = evaluator.evaluate(plan, payload, state, automationId, traceId);
                    if (result.getOutcome() == AutomationEvaluator.EvalOutcome.C1_NEGATIVE) {
                        result = foldInStrandedNegativeActions(result, plan, state, automationId);
                    }
                    if (!result.hasChanges()) {
                        writePostCasMemoryUpdates(automationId, result);
                        writeEvalSnapshot(automationId, plan, result, state);
                        publishLog(automationId, plan, user, payload, result);
                        return;
                    }
                    nextState = computeNextState(state, result, plan);
                } catch (Exception e) {
                    log.error("❌ [traceId={}] Evaluation failed on retry {}: {}",
                            traceId, attempt, e.getMessage(), e);
                    publishSkippedLog(automationId, plan, user, payload,
                            "Evaluation error on retry: " + e.getMessage(), traceId);
                    return;
                }
            }
            written = stateStore.compareAndSet(automationId, state.getVersion(), nextState);
        }

        if (!written) {
            log.warn("⚡ [traceId={}] CAS conflict unresolved after {} attempts", traceId, CAS_MAX_RETRIES);
            publishSkippedLog(automationId, plan, user, payload,
                    "CAS conflict — concurrent state update", traceId);
            return;
        }

        // ── 9. Post-CAS ───────────────────────────────────────────────────
        writePostCasScheduleKeys(result, automationId, plan);
        writeEvalSnapshot(automationId, plan, result, nextState);
        livePublisher.publish(plan, result, nextState, payload);
        dispatchResult(result, plan, payload, user, automationId);
    }


    // ─────────────────────────────────────────────────────────────────────
    // BUG 1 FIX — STRANDED DESCENDANT NEGATIVE ACTIONS
    // ─────────────────────────────────────────────────────────────────────

    private AutomationEvaluator.EvalResult foldInStrandedNegativeActions(
            AutomationEvaluator.EvalResult result,
            ExecutionPlan plan,
            AutomationRuntimeState prevState,
            String automationId) {

        if (plan.getConditionTree() == null || plan.getConditionTree().isEmpty()) return result;

        Map<String, Boolean> walkedThisTick =
                result.getConditionResults() != null ? result.getConditionResults() : Map.of();

        List<ExecutionPlan.CompiledAction> existing =
                result.getActionsToFire() != null ? result.getActionsToFire() : List.of();

        Set<String> seen = existing.stream()
                .map(a -> a.getDeviceId() + "|" + a.getKey() + "|" + a.getData())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<ExecutionPlan.CompiledAction> stranded = new ArrayList<>();
        Map<String, Boolean> extendedResults = new LinkedHashMap<>(walkedThisTick);
        long nowMs = System.currentTimeMillis();

        for (ExecutionPlan.CompiledConditionNode node : plan.getConditionTree()) {
            if (!node.isStateful()) continue;
            boolean wasActive = prevState.isNodeActive(node.getNodeId());
            boolean walkedThisNode = walkedThisTick.containsKey(node.getNodeId());

            if (!wasActive || walkedThisNode) continue; // not stranded

            ExecutionPlan.CompiledCondition c = node.getCondition();
            boolean hasGrace = c != null
                    && c.getDurationMinutes() > 0
                    && !"scheduled".equals(c.getConditionType());

            if (hasGrace) {
                long durationMs = c.getDurationMinutes() * 60_000L;
                Long armedAt = stateStore.getGraceArmedAtEpochMs(automationId, node.getNodeId());

                if (armedAt == null) {
                    // Parent just failed this tick, stranding this child for the first time.
                    // Start its grace clock and keep it ACTIVE — don't fire negatives yet,
                    // and don't mark it IDLE in extendedResults (leaving it unset means
                    // applyPerNodeActiveFlags() leaves the node's state untouched = still ACTIVE).
                    stateStore.armGrace(automationId, node.getNodeId(), nowMs,
                            c.getDurationMinutes() * 60L + 30);
                    log.info("⏳ Stranded node '{}' — parent false, honoring {}min child grace before negative actions",
                            node.getNodeId(), c.getDurationMinutes());
                    continue;
                } else if (nowMs - armedAt < durationMs) {
                    // Still within grace — keep holding, no negatives, no state change.
                    continue;
                } else {
                    // Grace expired — fall through and fire negatives below, clearing the timer.
                    stateStore.clearGrace(automationId, node.getNodeId());
                }
            }

            log.debug("🧩 Stranded descendant '{}' was active but not walked this tick — "
                            + "firing its {} negative action(s)",
                    node.getNodeId(), node.getNegativeActions() != null
                            ? node.getNegativeActions().size() : 0);

            if (node.getNegativeActions() != null) {
                for (ExecutionPlan.CompiledAction a : node.getNegativeActions()) {
                    String key = a.getDeviceId() + "|" + a.getKey() + "|" + a.getData();
                    if (seen.add(key)) stranded.add(a);
                }
            }
            extendedResults.put(node.getNodeId(), false);
        }

        if (stranded.isEmpty() && extendedResults.size() == walkedThisTick.size()) return result;

        List<ExecutionPlan.CompiledAction> combined = new ArrayList<>(existing);
        combined.addAll(stranded);

        return result.toBuilder()
                .actionsToFire(combined)
                .conditionResults(extendedResults)
                .build();
    }


    // ─────────────────────────────────────────────────────────────────────
    // COALITION SEQUENCE PROGRESS
    // ─────────────────────────────────────────────────────────────────────

    private void handleSequenceProgress(TriggerCoalition coalition,
                                        String firingDeviceId,
                                        AutomationRuntimeState state,
                                        CoalitionGuard.CoalitionStatus status,
                                        long nowMs) {
        if (status == CoalitionGuard.CoalitionStatus.SATISFIED) {
            state.setSequenceProgress(0);
        } else if (status == CoalitionGuard.CoalitionStatus.NOT_YET) {
            List<TriggerMember> ordered = coalition.getNonVetoMembers().stream()
                    .sorted(Comparator.comparingInt(TriggerMember::getSequenceIndex))
                    .toList();
            int progress = state.getSequenceProgress();
            if (progress < ordered.size()
                    && ordered.get(progress).getDeviceId().equals(firingDeviceId)) {
                state.setSequenceProgress(progress + 1);
            }
        }
    }


    // ─────────────────────────────────────────────────────────────────────
    // MEMORY UPDATE POST-CAS
    // ─────────────────────────────────────────────────────────────────────

    private void writePostCasMemoryUpdates(String automationId,
                                           AutomationEvaluator.EvalResult result) {
        Map<String, ConditionMemory> updates = result.getMemoryUpdates();
        if (updates == null || updates.isEmpty()) return;
        AutomationRuntimeState latest = stateStore.read(automationId);
        updates.forEach(latest::setConditionMemory);
        stateStore.forceWrite(automationId, latest);
    }


    // ─────────────────────────────────────────────────────────────────────
    // EVAL SNAPSHOT
    // ─────────────────────────────────────────────────────────────────────

    private void writeEvalSnapshot(String automationId,
                                   ExecutionPlan plan,
                                   AutomationEvaluator.EvalResult result,
                                   AutomationRuntimeState currentState) {
        try {
            AutomationRuntimeState.EvalSnapshot snapshot = new AutomationRuntimeState.EvalSnapshot();
            snapshot.setOutcome(result.getOutcome().name());
            snapshot.setTraceId(result.getTraceId());
            snapshot.setEvaluatedAt(result.getEvaluatedAt());
            snapshot.setC1True(result.isC1True());
            snapshot.setAnyWasActive(result.isAnyWasActive());
            snapshot.setReason(result.getReason());
            snapshot.setEvalDurationMs(result.getEvalDurationMs());
            snapshot.setConditionResults(result.getConditionResults());

            snapshot.setNodeStates(new HashMap<>(currentState.getNodeStates()));
            snapshot.setCoalitionLastFired(new HashMap<>(currentState.getTriggerMemberLastFired()));
            snapshot.setSequenceProgress(currentState.getSequenceProgress());

            if (plan.getConditionTree() != null && result.getMemoryUpdates() != null) {
                Map<String, String> summaries = new LinkedHashMap<>();
                for (ExecutionPlan.CompiledConditionNode node : plan.getConditionTree()) {
                    if (node.hasMemoryPolicy()) {
                        ConditionMemory mem = result.getMemoryUpdates().get(node.getNodeId());
                        if (mem != null) {
                            String s = evaluator.summarizeMemory(node.getMemoryPolicy(), mem);
                            summaries.put(node.getNodeId(), s);
                        }
                    }
                }
                snapshot.setConditionMemorySummaries(summaries);
            }

            AutomationRuntimeState latest = stateStore.read(automationId);
            latest.setLastEvalSnapshot(snapshot);
            stateStore.forceWrite(automationId, latest);
        } catch (Exception e) {
            log.warn("⚠️ Failed to write eval snapshot for '{}': {}", automationId, e.getMessage());
        }
    }


    // ─────────────────────────────────────────────────────────────────────
    // POST-CAS SCHEDULE KEYS
    // ─────────────────────────────────────────────────────────────────────

    private void writePostCasScheduleKeys(AutomationEvaluator.EvalResult result,
                                          String automationId,
                                          ExecutionPlan plan) {
        // Write schedule keys for both TRIGGERED and BRANCH_TRIGGERED outcomes —
        // both represent a successful positive dispatch that arms cooldown timers.
        if (result.getOutcome() != AutomationEvaluator.EvalOutcome.TRIGGERED
                && result.getOutcome() != AutomationEvaluator.EvalOutcome.BRANCH_TRIGGERED) return;
        if (plan.getConditionTree() == null) return;

        Map<String, Boolean> condResults = result.getConditionResults();
        if (condResults == null) return;

        ZonedDateTime now = ZonedDateTime.now(IST);
        String today = now.toLocalDate().toString();
        long ttlUntilMidnight = ChronoUnit.SECONDS.between(
                now, now.plusDays(1).truncatedTo(ChronoUnit.DAYS));

        for (ExecutionPlan.CompiledConditionNode node : plan.getConditionTree()) {
            if (!Boolean.TRUE.equals(condResults.get(node.getNodeId()))) continue;

            ExecutionPlan.CompiledCondition c = node.getCondition();
            if (c == null || !"scheduled".equals(c.getConditionType())) continue;

            String st = c.getScheduleType();
            String nodeId = node.getNodeId();

            if ("interval".equals(st)) {
                long intervalTtl = c.getIntervalMinutes() * 60L;
                stateStore.setIntervalKey(automationId, nodeId, intervalTtl);
                stateStore.setDailyIntervalKey(automationId, nodeId, today, ttlUntilMidnight);
                log.debug("⏱️ [{}] Interval cooldown: {}min (nodeId={})",
                        automationId, c.getIntervalMinutes(), nodeId);
            } else if ("solar".equals(st)) {
                stateStore.setDailySolarKey(automationId, today, ttlUntilMidnight);
            } else if ("at".equals(st) || st == null) {
                stateStore.setDailyFireKey(automationId, today, ttlUntilMidnight);
            }
        }
    }


    // ─────────────────────────────────────────────────────────────────────
    // STATE COMPUTATION
    // ─────────────────────────────────────────────────────────────────────

    /**
     * BUG 3 fix (carried forward) + BUG 4 fix:
     * <p>
     * BRANCH_TRIGGERED must NOT overwrite topLevelState. The top-level state
     * may already be "ACTIVE" (set by a previous TRIGGERED from another OR
     * branch) and must remain so. Only the per-node nodeStates are updated.
     * Setting it back to ACTIVE when it's already ACTIVE is harmless but
     * setting it to something else would corrupt the C1_NEGATIVE detection
     * logic (which looks at topLevelState == "ACTIVE" to decide whether
     * negative actions are warranted).
     */
    private AutomationRuntimeState computeNextState(AutomationRuntimeState current,
                                                    AutomationEvaluator.EvalResult result,
                                                    ExecutionPlan plan) {
        AutomationRuntimeState next = current.withNextVersion();

        if (result.getMemoryUpdates() != null) {
            result.getMemoryUpdates().forEach(next::setConditionMemory);
        }

        switch (result.getOutcome()) {
            case TRIGGERED -> {
                next.setTopLevelState("ACTIVE");
                next.setLastExecutionTime(new Date());
                applyPerNodeActiveFlags(next, plan, result.getConditionResults());
            }
            case BRANCH_TRIGGERED -> {
                // BUG 4 fix: a branch inside an already-active automation fired.
                // The top-level state stays ACTIVE (it was already set, or if this
                // is the very first branch to fire it should also become ACTIVE).
                // We deliberately do NOT call next.setTopLevelState("IDLE") here.
                next.setTopLevelState("ACTIVE");
                next.setLastExecutionTime(new Date());
                applyPerNodeActiveFlags(next, plan, result.getConditionResults());
            }
            case C1_NEGATIVE -> {
                next.setTopLevelState("IDLE");
                next.resetAllNodeStates();
//                applyPerNodeActiveFlags(next, plan, result.getConditionResults());
            }
            // SKIPPED, NOT_MET, FALLBACK, STATELESS_FIRE — no state change
            default -> {
            }
        }
        return next;
    }

    /**
     * Sets each stateful node's active flag strictly according to whether it
     * was walked and what it evaluated to THIS tick.
     */
    private void applyPerNodeActiveFlags(AutomationRuntimeState next,
                                         ExecutionPlan plan,
                                         Map<String, Boolean> conditionResults) {
        if (plan.getConditionTree() == null || conditionResults == null) return;
        for (ExecutionPlan.CompiledConditionNode node : plan.getConditionTree()) {
            if (!node.isStateful()) continue;
            Boolean walkedResult = conditionResults.get(node.getNodeId());
            if (walkedResult == null) continue; // not walked this tick — leave as-is
            next.setNodeState(node.getNodeId(), walkedResult ? "ACTIVE" : "IDLE");
        }
    }


    // ─────────────────────────────────────────────────────────────────────
    // BUG 2 FIX — DURATION WINDOW ARMING
    // ─────────────────────────────────────────────────────────────────────

    private void armDurationWindows(AutomationEvaluator.EvalResult result, String automationId) {
        Set<String> toArm = result.getIntervalNodesToArm();
        if (toArm == null || toArm.isEmpty()) return;

        ExecutionPlan plan = planCache.get(automationId);
        if (plan == null || plan.getConditionTree() == null) return;

        Map<String, ExecutionPlan.CompiledConditionNode> nodeMap = plan.getConditionTree().stream()
                .collect(Collectors.toMap(ExecutionPlan.CompiledConditionNode::getNodeId, n -> n));

        for (String nodeId : toArm) {
            ExecutionPlan.CompiledConditionNode node = nodeMap.get(nodeId);
            if (node == null || node.getCondition() == null) continue;
            long durationTtl = node.getCondition().getDurationMinutes() * 60L;
            if (durationTtl <= 0) continue;
            stateStore.setRunningKey(automationId, nodeId, durationTtl);
            log.info("⏱️ [{}] Duration window armed for '{}': {}min",
                    automationId, nodeId, node.getCondition().getDurationMinutes());
        }
    }


    // ─────────────────────────────────────────────────────────────────────
    // DISPATCH
    // ─────────────────────────────────────────────────────────────────────

    private void dispatchResult(AutomationEvaluator.EvalResult result,
                                ExecutionPlan plan,
                                Map<String, Object> payload,
                                String user,
                                String automationId) {
        String name = resolveAutomationName(automationId);
        String traceId = result.getTraceId();
        String homeId = plan.getHomeId();

        switch (result.getOutcome()) {

            case STATELESS_FIRE, FALLBACK -> dispatcher.dispatch(result.getActionsToFire(), payload, user,
                            automationId, name, traceId, homeId)
                    .thenRun(() -> publishLog(automationId, plan, user, payload, result));

            case TRIGGERED -> {
                List<ExecutionPlan.CompiledAction> actions =
                        result.getActionsToFire() != null && !result.getActionsToFire().isEmpty()
                                ? result.getActionsToFire()
                                : (plan.getTopLevelPositiveActions() != null
                                   ? plan.getTopLevelPositiveActions() : List.of());
                armDurationWindows(result, automationId);
                dispatcher.dispatch(actions, payload, user, automationId, name, traceId, homeId)
                        .thenRun(() -> {
                            log.info("🚀 [{}] Triggered", name);

                            dispatcher.notifyTriggered(name, homeId);
                            publishLog(automationId, plan, user, payload, result);
                        });
            }

            case BRANCH_TRIGGERED -> {
                // BUG 4 fix: dispatch per-branch actions assembled by the evaluator.
                // Do NOT fall back to plan.getTopLevelPositiveActions() — those belong
                // to the TRIGGERED path. The branch actions are already in actionsToFire.
                List<ExecutionPlan.CompiledAction> actions =
                        result.getActionsToFire() != null && !result.getActionsToFire().isEmpty()
                                ? result.getActionsToFire() : List.of();

                if (actions.isEmpty()) {
                    log.warn("⚠️ [{}] BRANCH_TRIGGERED but actionsToFire is empty — nothing to dispatch",
                            name);
                    publishLog(automationId, plan, user, payload, result);
                    return;
                }
                armDurationWindows(result, automationId);
                dispatcher.dispatch(actions, payload, user, automationId, name, traceId, homeId)
                        .thenRun(() -> {
                            log.info("🌿 [{}] Branch triggered — {} action(s) dispatched",
                                    name, actions.size());

                            dispatcher.notifyTriggered(name, homeId);
                            publishLog(automationId, plan, user, payload, result);
                        });
            }

            case C1_NEGATIVE -> {
                // BUG 1 fix: actionsToFire already includes stranded descendant
                // negative actions, folded in by foldInStrandedNegativeActions().
                List<ExecutionPlan.CompiledAction> toFire = result.getActionsToFire() != null
                        ? new ArrayList<>(result.getActionsToFire()) : new ArrayList<>();

                dispatcher.dispatch(toFire, payload, user, automationId, name, traceId, homeId)
                        .thenRun(() -> {
                            log.debug("[{}] — trigger condition lost", name);
                            notificationService.sendNotification(
                                    name + " — trigger condition lost", "info", homeId);
                            publishLog(automationId, plan, user, payload, result);
                        });
            }

            default -> publishLog(automationId, plan, user, payload, result);
        }
    }


    // ─────────────────────────────────────────────────────────────────────
    // PLAN INVALIDATION
    // ─────────────────────────────────────────────────────────────────────

    public void invalidatePlan(String automationId) {
        evictLocalCaches(automationId);
        redisTemplate.convertAndSend(PLAN_INVALIDATE_CHANNEL, automationId);
        log.info("📡 Plan invalidation published for '{}'", automationId);
    }

    public void evictLocalCaches(String automationId) {
        planCache.evict(automationId);
        nameCache.remove(automationId);
    }

    public void updatePlan(String automationId, ExecutionPlan plan) {
        planCache.put(automationId, plan);
        nameCache.put(automationId, plan.getAutomationName());
        redisTemplate.convertAndSend(PLAN_INVALIDATE_CHANNEL, automationId);
        log.info("📡 Plan updated and invalidation published for '{}'", automationId);
    }


    // ─────────────────────────────────────────────────────────────────────
    // LOGGING
    // ─────────────────────────────────────────────────────────────────────

    private void publishLog(String automationId, ExecutionPlan plan,
                            String user, Map<String, Object> payload,
                            AutomationEvaluator.EvalResult result) {
        AutomationLog.LogStatus status = switch (result.getOutcome()) {
            // BUG 4 fix: BRANCH_TRIGGERED is a successful dispatch — map to TRIGGERED
            case TRIGGERED, BRANCH_TRIGGERED, STATELESS_FIRE, FALLBACK -> AutomationLog.LogStatus.TRIGGERED;
            case C1_NEGATIVE -> AutomationLog.LogStatus.TRIGGER_FALSE;
            case SKIPPED -> AutomationLog.LogStatus.SKIPPED;
            case NOT_MET -> AutomationLog.LogStatus.NOT_MET;
        };

        logStream.publish(AutomationLog.builder()
                .automationId(automationId)
                .automationName(resolveAutomationName(automationId))
                .user(user)
                .triggerDeviceId(plan.getTriggerDeviceId())
                .timestamp(new Date())
                .payload(payload != null ? payload : Map.of())
                .status(status)
                .reason(result.getReason() != null ? result.getReason() : result.getOutcome().name())
                .traceId(result.getTraceId())
                .evalDurationMs(result.getEvalDurationMs())
                .build());
    }

    private void publishSkippedLog(String automationId, ExecutionPlan plan,
                                   String user, Map<String, Object> payload,
                                   String reason, String traceId) {
        logStream.publish(AutomationLog.builder()
                .automationId(automationId)
                .automationName(resolveAutomationName(automationId))
                .user(user)
                .triggerDeviceId(plan != null ? plan.getTriggerDeviceId() : "")
                .timestamp(new Date())
                .payload(payload != null ? payload : Map.of())
                .status(AutomationLog.LogStatus.SKIPPED)
                .reason(reason)
                .traceId(traceId)
                .build());
    }


    // ─────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────

    private String resolveAutomationName(String automationId) {
        return nameCache.computeIfAbsent(automationId, id ->
                automationRepository.findById(id)
                        .map(Automation::getName)
                        .orElse(id));
    }
}