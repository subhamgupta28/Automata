package dev.automata.automata.v2;

import dev.automata.automata.dto.AutomationRuntimeState;
import dev.automata.automata.dto.BranchDecision;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Core automation orchestrator.
 * <p>
 * Changes vs previous version
 * ───────────────────────────
 * Point 1 — Coalition guard
 * execute() now accepts an optional firingDeviceId parameter.
 * Before evaluation, it records the firing device in runtime state and
 * calls CoalitionGuard.evaluate(). If the guard returns NOT_YET or VETOED,
 * execution is skipped (state is still written to persist the lastFired timestamp).
 * <p>
 * Point 2 — Memory persistence
 * After a successful CAS write, writePostCasMemoryUpdates() copies
 * EvalResult.memoryUpdates into nextState and writes the state again.
 * Because memory updates don't affect correctness (only diagnostics and
 * policy evaluation), they bypass the CAS version check — last write wins.
 * This avoids adding latency to the critical CAS path.
 * <p>
 * IMPORTANT: memoryUpdates ARE included in the CAS-protected nextState for
 * TRIGGERED/RESTORED/C1_NEGATIVE outcomes (where state must be consistent).
 * The post-CAS write is only for SKIPPED/NOT_MET outcomes where the core
 * state didn't change but memory still needs to advance.
 * <p>
 * Point 9 — Eval snapshot
 * After every execution (whether state changed or not), writeEvalSnapshot()
 * writes a lightweight diagnostic snapshot into the runtime state.
 * This does NOT participate in CAS — it's a best-effort last-write-wins write.
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
    private final CoalitionGuard coalitionGuard;         // Point 1

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int CAS_MAX_RETRIES = 2;
    private static final String PLAN_INVALIDATE_CHANNEL = "automation:plan:invalidated";

    private final ConcurrentHashMap<String, String> nameCache = new ConcurrentHashMap<>();


    /**
     * Runs every 30 minutes.
     * Adjust fixedDelay / cron to taste — the job is idempotent.
     */
    @Scheduled(fixedDelay = 30 * 60 * 1_000)   // 30 min
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

    // ─────────────────────────────────────────────────────────────────────
    // Core logic
    // ─────────────────────────────────────────────────────────────────────

    private void recompileIfNeeded(Automation automation, AtomicInteger counter) {

        String id = automation.getId();
        String name = automation.getName();

        ExecutionPlan cached = planCache.get(id);

        // ── Case 1: plan is completely absent ────────────────────────────
        if (cached == null) {
            log.warn("⚠️ [reconciler] Plan missing for '{}' — recompiling", name);
            recompile(automation, "missing from cache");
            counter.incrementAndGet();
            return;
        }

        // ── Case 2: plan is present but stale ────────────────────────────
        // automation.getUpdateDate() is set by your save service whenever the
        // user edits the automation. compiledAt is stamped by the compiler.
        Date updatedAt = automation.getUpdateDate();
        Date compiledAt = cached.getCompiledAt();

        if (isStale(updatedAt, compiledAt)) {
            log.warn("⚠️ [reconciler] Plan stale for '{}' (DB={}, plan={}) — recompiling",
                    name, updatedAt, compiledAt);
            recompile(automation, "stale (DB newer than cache)");
            counter.incrementAndGet();
        }
    }

    /**
     * Compiles and pushes the plan into both the in-process cache and Redis.
     * Mirrors what AutomationService does on save — kept in one method so the
     * write path is identical regardless of who triggers it.
     */
    private void recompile(Automation automation, String reason) {
        String id = automation.getId();
        String name = automation.getName();

        ExecutionPlan plan = planCompiler.compile(automation);

        // Push to in-process cache + publish Redis invalidation to all nodes
        updatePlan(id, plan);

        // Persist plan blob to Redis so other nodes can warm from it on startup
        stateStore.writePlan(id, plan);

        log.info("✅ [reconciler] '{}' recompiled — reason: {}", name, reason);
    }

    /**
     * Returns true when the DB record is newer than the compiled plan by more
     * than a small clock-skew buffer.
     * <p>
     * The buffer (2 s) prevents a harmless race where the compiler stamps
     * compiledAt a few milliseconds before the DB write's updatedAt is
     * committed, causing a perpetual false-positive on every reconciler cycle.
     */
    private boolean isStale(Date updatedAt, Date compiledAt) {
        if (updatedAt == null || compiledAt == null) return true;
        long SKEW_BUFFER_MS = 2_000;
        return updatedAt.getTime() > compiledAt.getTime() + SKEW_BUFFER_MS;
    }


    // ─────────────────────────────────────────────────────────────────────
    // MAIN ENTRY POINT
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Overload for the legacy single-device path (AutomationService.handleAction,
     * triggerPeriodicAutomations). firingDeviceId defaults to plan.triggerDeviceId.
     */
    @Async("automationExecutor")
    public void execute(String automationId, Map<String, Object> payload, String user) {
        ExecutionPlan plan = planCache.get(automationId);
        String firingDeviceId = plan != null ? plan.getTriggerDeviceId() : null;
        executeInternal(automationId, payload, user, firingDeviceId);
    }

    /**
     * Coalition-aware entry point — called when a specific device fired.
     * AutomationService calls this overload when it knows which device triggered.
     */
    @Async("automationExecutor")
    public void execute(String automationId, Map<String, Object> payload,
                        String user, String firingDeviceId) {
        executeInternal(automationId, payload, user, firingDeviceId);
    }

    private void executeInternal(String automationId, Map<String, Object> payload,
                                 String user, String firingDeviceId) {

        String traceId = automationId.substring(0, Math.min(8, automationId.length()))
                + "-" + System.currentTimeMillis()
                + "-" + Long.toHexString(ThreadLocalRandom.current().nextLong() & 0xFFFFFFFFL);

        log.debug("🔍 [traceId={}] execute start — automation='{}' user='{}' firingDevice='{}'",
                traceId, automationId, user, firingDeviceId);

        // 1. Load plan
        ExecutionPlan plan = planCache.get(automationId);
        if (plan == null) {
            log.warn("⏭️ [traceId={}] No execution plan for '{}' — skipping.", traceId, automationId);
            return;
        }

        // 2. Snooze / timed-disable
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

        // 3. Read state
        AutomationRuntimeState state = stateStore.read(automationId);

        // ── Point 1: Coalition guard ───────────────────────────────────────
        if (plan.hasCoalition() && firingDeviceId != null) {
            long nowMs = System.currentTimeMillis();

            // Record this member's fire time in a provisional next-state.
            // We write this unconditionally (even if coalition not satisfied) so that
            // subsequent members can see that this one has fired.
            AutomationRuntimeState stateWithMember = state.withNextVersion();
            stateWithMember.recordMemberFired(firingDeviceId, nowMs);

            CoalitionGuard.CoalitionResult coalitionResult =
                    coalitionGuard.evaluate(plan.getTriggerCoalition(),
                            firingDeviceId, stateWithMember, nowMs);

            log.debug("🤝 [{}] Coalition: {} — {}", plan.getAutomationName(),
                    coalitionResult.status(), coalitionResult.reason());

            if (!coalitionResult.shouldProceed()) {
                // Persist the updated lastFired timestamp even though we skip evaluation.
                // Use forceWrite (no CAS) because lastFired is a best-effort timestamp;
                // a concurrent write from another member is acceptable.
                stateStore.forceWrite(automationId, stateWithMember);
                publishSkippedLog(automationId, plan, user, payload,
                        "Coalition " + coalitionResult.status() + ": " + coalitionResult.reason(),
                        traceId);
                return;
            }

            // Coalition satisfied — use the updated state (with this member's fire recorded)
            state = stateWithMember;

            // For SEQUENCE coalitions: advance or reset sequenceProgress
            if (plan.getTriggerCoalition().getMode() == TriggerCoalition.CoalitionMode.SEQUENCE) {
                handleSequenceProgress(plan.getTriggerCoalition(), firingDeviceId, state,
                        coalitionResult.status(), nowMs);
            }
        }

        // 4. Evaluate (pure — no side effects)
        AutomationEvaluator.EvalResult result;
        try {
            result = evaluator.evaluate(plan, payload, state, automationId, traceId);
        } catch (Exception e) {
            log.error("❌ [traceId={}] Evaluation failed: {}", traceId, e.getMessage(), e);
            publishSkippedLog(automationId, plan, user, payload,
                    "Evaluation error: " + e.getMessage(), traceId);
            return;
        }

        log.debug("📋 [traceId={}] outcome={} c1={} anyWasActive={}",
                traceId, result.getOutcome(), result.isC1True(), result.isAnyWasActive());

        // 5. No state change needed — but still persist memory updates and snapshot
        if (!result.hasChanges()) {
            writePostCasMemoryUpdates(automationId, result, state);
            writeEvalSnapshot(automationId, plan, result, state);
            livePublisher.publish(plan, result, state, payload);
            publishLog(automationId, plan, user, payload, result);
            return;
        }

        // 6. Compute next state (pure)
        AutomationRuntimeState nextState = computeNextState(state, result, plan);

        // 7. CAS write with retry
        boolean written = false;
        for (int attempt = 0; attempt < CAS_MAX_RETRIES && !written; attempt++) {
            if (attempt > 0) {
                log.debug("🔄 [traceId={}] CAS retry attempt {}", traceId, attempt);
                try {
                    state = stateStore.read(automationId);
                    result = evaluator.evaluate(plan, payload, state, automationId, traceId);
                    if (!result.hasChanges()) {
                        writePostCasMemoryUpdates(automationId, result, state);
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
            log.warn("⚡ [traceId={}] CAS conflict unresolved for '{}' after {} attempts",
                    traceId, automationId, CAS_MAX_RETRIES);
            publishSkippedLog(automationId, plan, user, payload,
                    "CAS conflict — concurrent state update", traceId);
            return;
        }

        // 8. POST-CAS: write schedule keys
        writePostCasScheduleKeys(result, automationId);

        // 9. POST-CAS: write eval snapshot for inspection API (Point 9)
        writeEvalSnapshot(automationId, plan, result, nextState);
        livePublisher.publish(plan, result, nextState, payload);
        // 10. Dispatch actions
        dispatchResult(result, plan, payload, user, automationId, state);
    }


    // ─────────────────────────────────────────────────────────────────────
    // POINT 1 — SEQUENCE PROGRESS
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Advances or resets sequenceProgress after a coalition evaluation.
     * Called in-place on the state object (before CAS write — sequenceProgress
     * is part of the state blob and protected by CAS).
     */
    private void handleSequenceProgress(TriggerCoalition coalition,
                                        String firingDeviceId,
                                        AutomationRuntimeState state,
                                        CoalitionGuard.CoalitionStatus status,
                                        long nowMs) {
        if (status == CoalitionGuard.CoalitionStatus.SATISFIED) {
            // Sequence complete — reset for next cycle
            state.setSequenceProgress(0);
        } else if (status == CoalitionGuard.CoalitionStatus.NOT_YET) {
            // Check if the firing device was the expected next in sequence
            List<TriggerMember> ordered = coalition.getNonVetoMembers().stream()
                    .sorted(Comparator.comparingInt(TriggerMember::getSequenceIndex))
                    .toList();
            int progress = state.getSequenceProgress();
            if (progress < ordered.size()
                    && ordered.get(progress).getDeviceId().equals(firingDeviceId)) {
                // Correct next member — advance
                state.setSequenceProgress(progress + 1);
            }
            // Out-of-order or timed-out — leave as-is (guard already logged the reset signal)
        }
    }


    // ─────────────────────────────────────────────────────────────────────
    // POINT 2 — MEMORY UPDATE POST-CAS
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Writes memory updates into the stored state for SKIPPED/NOT_MET outcomes.
     * <p>
     * For state-changing outcomes (TRIGGERED etc.), memory is already included
     * in nextState by computeNextState() and written via CAS. This method handles
     * the case where the core state didn't change but memory still needs to advance
     * (e.g. DURATION timer must keep ticking even when the threshold isn't met yet).
     * <p>
     * Uses forceWrite (no CAS) — memory is best-effort; a concurrent write from
     * another thread may overwrite it, which is acceptable for diagnostic state.
     */
    private void writePostCasMemoryUpdates(String automationId,
                                           AutomationEvaluator.EvalResult result,
                                           AutomationRuntimeState currentState) {
        Map<String, ConditionMemory> updates = result.getMemoryUpdates();
        if (updates == null || updates.isEmpty()) return;

        // Re-read to get latest state (avoid overwriting concurrent CAS writes)
        AutomationRuntimeState latest = stateStore.read(automationId);
        updates.forEach(latest::setConditionMemory);
        stateStore.forceWrite(automationId, latest);
    }


    // ─────────────────────────────────────────────────────────────────────
    // POINT 9 — EVAL SNAPSHOT
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Writes a lightweight diagnostic snapshot into the stored state.
     * The snapshot is best-effort — it's not version-guarded.
     * The inspection API endpoint reads this snapshot via stateStore.read().
     */
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

            // Branch states at time of eval
            snapshot.setBranchStates(new HashMap<>(currentState.getBranchStates()));

            // Coalition tracking
            snapshot.setCoalitionLastFired(new HashMap<>(currentState.getTriggerMemberLastFired()));
            snapshot.setSequenceProgress(currentState.getSequenceProgress());

            // Memory summaries (human-readable) — built from conditionMemories + plan nodes
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

            // Re-read latest state and attach snapshot (best-effort)
            AutomationRuntimeState latest = stateStore.read(automationId);
            latest.setLastEvalSnapshot(snapshot);
            stateStore.forceWrite(automationId, latest);
        } catch (Exception e) {
            log.warn("⚠️ Failed to write eval snapshot for '{}': {}", automationId, e.getMessage());
        }
    }


    // ─────────────────────────────────────────────────────────────────────
    // POST-CAS SCHEDULE KEY WRITES
    // ─────────────────────────────────────────────────────────────────────

    private void writePostCasScheduleKeys(AutomationEvaluator.EvalResult result,
                                          String automationId) {
        if (result.getOutcome() != AutomationEvaluator.EvalOutcome.TRIGGERED) return;
        if (result.getBranchDecisions() == null) return;

        ZonedDateTime now = ZonedDateTime.now(IST);
        String today = now.toLocalDate().toString();
        long ttlUntilMidnight = ChronoUnit.SECONDS.between(
                now, now.plusDays(1).truncatedTo(ChronoUnit.DAYS));

        for (BranchDecision d : result.getBranchDecisions()) {
            if (d.getType() != BranchDecision.Type.TRIGGER) continue;
            ExecutionPlan.CompiledBranch branch = d.getBranch();
            ExecutionPlan.CompiledCondition gc = branch.getGateCondition();
            if (gc == null) continue;

            String st = gc.getScheduleType();
            // Use gateCondition.getNodeId() for schedule keys (always plain condition ID)
            // Use branch.getGateNodeId() only for branchState and runningKey (needs uniqueness)
            String scheduleKeyNodeId = gc.getNodeId();  // always "node_condition_1"
            String branchKeyNodeId = branch.getGateNodeId();  // "node_condition_1" or "node_condition_1@node_and_6"
            if ("interval".equals(st)) {
                long intervalTtl = gc.getIntervalMinutes() * 60L;
                stateStore.setIntervalKey(automationId, scheduleKeyNodeId, intervalTtl);
                log.info("⏱️ [{}] Interval cooldown armed: {}min (key TTL={}s)",
                        automationId, gc.getIntervalMinutes(), intervalTtl);
                stateStore.setDailyIntervalKey(automationId, scheduleKeyNodeId, today, ttlUntilMidnight);
            } else if ("solar".equals(st)) {
                stateStore.setDailySolarKey(automationId, today, ttlUntilMidnight);
            } else if ("at".equals(st) || st == null) {
                stateStore.setDailyFireKey(automationId, today, ttlUntilMidnight);
            }
        }
    }


    // ─────────────────────────────────────────────────────────────────────
    // STATE COMPUTATION (pure)
    // ─────────────────────────────────────────────────────────────────────

    private AutomationRuntimeState computeNextState(AutomationRuntimeState current,
                                                    AutomationEvaluator.EvalResult result,
                                                    ExecutionPlan plan) {
        AutomationRuntimeState next = current.withNextVersion();

        // Point 2: always carry forward memory updates into the CAS-protected next state
        if (result.getMemoryUpdates() != null) {
            result.getMemoryUpdates().forEach(next::setConditionMemory);
        }

        switch (result.getOutcome()) {
            case TRIGGERED -> {
                if (!plan.hasBranches()) {
                    String s = result.getNextTopLevelState();
                    next.setTopLevelState(s != null ? s : "ACTIVE");
                    next.setLastExecutionTime(new Date());
                    // Mark condition tree nodes active for hysteresis
                    if (plan.getConditionTree() != null)
                        plan.getConditionTree().forEach(n -> {
                            if (n.isStateful()) next.setNodeActive(n.getNodeId(), true);
                        });
                } else {
                    // Branch automation: mark condition tree nodes active
                    if (plan.getConditionTree() != null)
                        plan.getConditionTree().forEach(n -> {
                            if (n.isStateful()) next.setNodeActive(n.getNodeId(), true);
                        });
                    applyBranchDecisions(result, next);
                }
            }
            case RESTORED -> {
                // Mark condition tree nodes inactive so hysteresis resets cleanly
                if (plan.getConditionTree() != null)
                    plan.getConditionTree().forEach(n -> next.setNodeActive(n.getNodeId(), false));
                applyBranchDecisions(result, next);
            }
            case C1_NEGATIVE -> {
                if (plan.hasBranches())
                    next.getBranchStates().replaceAll((k, v) -> "IDLE");
                if (plan.getConditionTree() != null)
                    plan.getConditionTree().forEach(n -> next.setNodeActive(n.getNodeId(), false));
                next.setTopLevelState("IDLE");
            }
            default -> { /* SKIPPED, NOT_MET, FALLBACK, STATELESS_FIRE — no state change */ }
        }
        return next;
    }

    private void applyBranchDecisions(AutomationEvaluator.EvalResult result,
                                      AutomationRuntimeState next) {
        if (result.getBranchDecisions() == null) return;
        for (BranchDecision d : result.getBranchDecisions()) {
            String nodeId = d.getBranch().getGateNodeId();
            switch (d.getType()) {
                case TRIGGER -> {
                    next.setBranchState(nodeId, d.getBranch().hasDuration() ? "HOLDING" : "ACTIVE");
                    next.setLastExecutionTime(new Date());
                }
                case REVERT, DURATION_EXPIRED -> next.setBranchState(nodeId, "IDLE");
                default -> {
                }
            }
        }
    }


    // ─────────────────────────────────────────────────────────────────────
    // DISPATCH
    // ─────────────────────────────────────────────────────────────────────

    private void dispatchResult(AutomationEvaluator.EvalResult result,
                                ExecutionPlan plan,
                                Map<String, Object> payload,
                                String user,
                                String automationId,
                                AutomationRuntimeState prevState) {
        String name = resolveAutomationName(automationId);
        String traceId = result.getTraceId();

        switch (result.getOutcome()) {

            case STATELESS_FIRE, FALLBACK -> dispatcher.dispatch(result.getActionsToFire(), payload, user,
                            automationId, name, traceId)
                    .thenRun(() -> publishLog(automationId, plan, user, payload, result));

            case TRIGGERED -> {
                if (!plan.hasBranches()) {
                    List<ExecutionPlan.CompiledAction> actions =
                            result.getActionsToFire() != null && !result.getActionsToFire().isEmpty()
                                    ? result.getActionsToFire()
                                    : (plan.getTopLevelPositiveActions() != null
                                       ? plan.getTopLevelPositiveActions() : List.of());
                    dispatcher.dispatch(actions, payload, user, automationId, name, traceId)
                            .thenRun(() -> {
                                dispatcher.notifyTriggered(name);
                                publishLog(automationId, plan, user, payload, result);
                            });
                } else {
                    dispatchBranchDecisions(result, plan, payload, user, automationId, name);
                }
            }

            case RESTORED -> dispatchBranchDecisions(result, plan, payload, user, automationId, name);

            case C1_NEGATIVE -> {
                // Collect tree-walk negative actions + active branch negative actions (deduplicated)
                List<ExecutionPlan.CompiledAction> toFire = result.getActionsToFire() != null
                        ? new ArrayList<>(result.getActionsToFire()) : new ArrayList<>();

                if (plan.hasBranches()) {
                    Set<String> seen = toFire.stream()
                            .map(a -> a.getDeviceId() + "|" + a.getKey() + "|" + a.getData())
                            .collect(Collectors.toCollection(LinkedHashSet::new));
                    plan.getBranches().stream()
                            .filter(b -> prevState.isBranchActive(b.getGateNodeId()))
                            .forEach(b -> {
                                stateStore.deleteRunningKey(automationId, b.getGateNodeId());
                                if (b.getNegativeActions() != null)
                                    b.getNegativeActions().stream()
                                            .filter(a -> seen.add(
                                                    a.getDeviceId() + "|" + a.getKey() + "|" + a.getData()))
                                            .forEach(toFire::add);
                            });
                }

                dispatcher.dispatch(toFire, payload, user, automationId, name, traceId)
                        .thenRun(() -> {
                            notificationService.sendNotification(
                                    name + " — trigger condition lost", "info");
                            publishLog(automationId, plan, user, payload, result);
                        });
            }

            default -> publishLog(automationId, plan, user, payload, result);
        }
    }

    private void dispatchBranchDecisions(AutomationEvaluator.EvalResult result,
                                         ExecutionPlan plan,
                                         Map<String, Object> payload,
                                         String user,
                                         String automationId,
                                         String automationName) {
        if (result.getBranchDecisions() == null) return;

        List<CompletableFuture<Void>> allFutures = new ArrayList<>();

        for (BranchDecision decision : result.getBranchDecisions()) {
            ExecutionPlan.CompiledBranch branch = decision.getBranch();
            String gateNodeId = branch.getGateNodeId();
            String branchDesc = describeBranch(branch);

            switch (decision.getType()) {

                case TRIGGER -> {
                    CompletableFuture<Void> f =
                            dispatcher.dispatch(branch.getPositiveActions(), payload,
                                            user, automationId, automationName, result.getTraceId())
                                    .thenAccept(ok -> {
                                        if (!ok) {
                                            log.warn("⚠️ [{}] '{}' positive dispatch failed",
                                                    automationName, branchDesc);
                                            return;
                                        }
                                        if (branch.hasDuration()) {
                                            long durationTtl =
                                                    branch.getGateCondition().getDurationMinutes() * 60L;
                                            stateStore.setRunningKey(automationId, gateNodeId, durationTtl);
                                            log.info("⏱️ [{}] '{}' running for {}min",
                                                    automationName, branchDesc,
                                                    branch.getGateCondition().getDurationMinutes());
                                        }
                                        dispatcher.notifyTriggered(automationName);
                                        log.info("🚀 [{}] '{}' triggered (priority {})",
                                                automationName, branchDesc, branch.getPriority());
                                    });
                    allFutures.add(f);
                }

                case REVERT, DURATION_EXPIRED -> {
                    String reason = decision.getReason();
                    CompletableFuture<Void> f =
                            dispatcher.dispatch(branch.getNegativeActions(), payload,
                                            user, automationId, automationName, result.getTraceId())
                                    .thenAccept(ok -> {
                                        if (ok) {
                                            stateStore.deleteRunningKey(automationId, gateNodeId);
                                            dispatcher.notifyReverted(automationName, branchDesc);
                                            log.info("⏹️ [{}] '{}' → IDLE — {}",
                                                    automationName, branchDesc, reason);
                                        }
                                    });
                    allFutures.add(f);
                }

                case SUPPRESSED -> log.debug("⏸️ [{}] '{}' suppressed — {}",
                        automationName, branchDesc, decision.getReason());

                default -> {
                }
            }
        }

        CompletableFuture.allOf(allFutures.toArray(new CompletableFuture[0]))
                .thenRun(() -> publishLog(automationId, plan, user, payload, result))
                .exceptionally(ex -> {
                    log.error("❌ [{}] Branch dispatch error: {}", automationName, ex.getMessage(), ex);
                    publishSkippedLog(automationId, plan, user, payload,
                            "Branch dispatch error: " + ex.getMessage(), result.getTraceId());
                    return null;
                });
    }


    // ─────────────────────────────────────────────────────────────────────
    // PLAN INVALIDATION
    // ─────────────────────────────────────────────────────────────────────

    public void invalidatePlan(String automationId) {
        planCache.evict(automationId);
        nameCache.remove(automationId);
        redisTemplate.convertAndSend(PLAN_INVALIDATE_CHANNEL, automationId);
        log.info("📡 Plan invalidation published for '{}'", automationId);
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
            case TRIGGERED, STATELESS_FIRE, FALLBACK -> AutomationLog.LogStatus.TRIGGERED;
            case RESTORED -> AutomationLog.LogStatus.RESTORED;
            case C1_NEGATIVE -> AutomationLog.LogStatus.TRIGGER_FALSE;
            case SKIPPED -> AutomationLog.LogStatus.SKIPPED;
            case NOT_MET -> AutomationLog.LogStatus.NOT_MET;
            case SUPPRESSED -> AutomationLog.LogStatus.SUPPRESSED;
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

    private String describeBranch(ExecutionPlan.CompiledBranch branch) {
        ExecutionPlan.CompiledCondition gc = branch.getGateCondition();
        if (gc == null) return branch.getGateNodeId();
        if ("scheduled".equals(gc.getConditionType())) {
            return switch (Optional.ofNullable(gc.getScheduleType()).orElse("at")) {
                case "range" -> "Time " + gc.getFromTime() + "-" + gc.getToTime();
                case "interval" -> "Every " + gc.getIntervalMinutes() + "min";
                case "solar" -> gc.getSolarType();
                default -> "At " + gc.getTime();
            };
        }
        String k = gc.getTriggerKey() != null ? gc.getTriggerKey() : "value";
        return switch (gc.getConditionType()) {
            case "above" -> k + ">" + gc.getValue();
            case "below" -> k + "<" + gc.getValue();
            case "range" -> k + " in " + gc.getAbove() + "-" + gc.getBelow();
            default -> k + "=" + gc.getValue();
        };
    }
}