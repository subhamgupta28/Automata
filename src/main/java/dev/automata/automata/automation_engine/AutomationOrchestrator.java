package dev.automata.automata.automation_engine;

import dev.automata.automata.automation_engine.enums.EvalOutcome;
import dev.automata.automata.automation_engine.evaluator.DispatchContext;
import dev.automata.automata.automation_engine.evaluator.OutcomeHandlerRegistry;
import dev.automata.automata.automation_engine.guard.PreExecutionGuard;
import dev.automata.automata.dto.AutomationRuntimeState;
import dev.automata.automata.dto.ConditionMemory;
import dev.automata.automata.model.AutomationLog;
import dev.automata.automata.model.AutomationStateSnapshot;
import dev.automata.automata.repository.AutomationStateSnapshotRepository;
import dev.automata.automata.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Core automation orchestrator.
 *
 * <p>Coordinates a single automation evaluation cycle end-to-end: loads the
 * compiled {@link ExecutionPlan}, runs pre-execution guards (snooze,
 * timed-disable, coalition), delegates to {@link AutomationEvaluator} for
 * pure condition evaluation, persists the resulting state via
 * compare-and-set with retry, and dispatches the outcome's actions.
 *
 * <p>Outcome handling is delegated to {@link OutcomeHandlerRegistry} /
 * {@link dev.automata.automata.automation_engine.evaluator.OutcomeHandlerRegistry},
 * which supplies per-{@link EvalOutcome} behavior for whether the outcome
 * has state changes, arms schedule keys, persists a snapshot, maps to a
 * {@link AutomationLog.LogStatus}, and how it transitions runtime state and
 * dispatches actions.
 *
 * <p>Handles both top-level activation (a single condition chain becoming
 * ACTIVE) and per-branch activation within an OR fanout, via
 * {@link EvalOutcome#BRANCH_TRIGGERED}: a branch node transitioning
 * inactive→active while the top-level automation is already ACTIVE.
 * BRANCH_TRIGGERED dispatches the branch's own per-node positive actions
 * (already present in {@code result.getActionsToFire()}) rather than
 * {@code plan.getTopLevelPositiveActions()}, updates only the affected
 * node's entry in {@code nodeStates} without touching {@code topLevelState}
 * (which may already be ACTIVE and must remain so), and is logged and
 * treated identically to {@code TRIGGERED} for dispatch and change-detection
 * purposes.
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
    private final NotificationService notificationService;
    private final AutomationLivePublisher livePublisher;
    private final List<PreExecutionGuard> preExecutionGuards;
    private final AutomationStateSnapshotRepository stateSnapshotRepository;
    private final OutcomeHandlerRegistry outcomeHandlers;
    private final PlanReconciliationService planReconciliationService;
    private final EvalSnapshotWriter evalSnapshotWriter;
    private final StrandedActionResolver strandedActionResolver;

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int CAS_MAX_RETRIES = 2;


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
    public void execute(
            String automationId, Map<String, Object> payload,
            String user, String firingDeviceId, String homeId
    ) {
        executeInternal(automationId, payload, user, firingDeviceId, homeId);
    }

    private void executeInternal(
            String automationId, Map<String, Object> payload,
            String user, String firingDeviceId, String homeId
    ) {

        String traceId = automationId.substring(0, Math.min(8, automationId.length()))
                + "-" + System.currentTimeMillis()
                + "-" + Long.toHexString(ThreadLocalRandom.current().nextLong() & 0xFFFFFFFFL);

        log.debug("🔍 [traceId={}] execute — automation='{}' firingDevice='{}'",
                traceId, automationId, firingDeviceId);

        // ── Load plan ──────────────────────────────────────────────────
        Optional<ExecutionPlan> maybePlan = planReconciliationService.ensureFresh(automationId, traceId);
        if (maybePlan.isEmpty()) return; // already logged inside ensureFresh()
        ExecutionPlan plan = maybePlan.get();

        // ── Pre-execution guards (snooze, timed-disable, coalition) ──
        AutomationRuntimeState state = stateStore.read(automationId);
        long nowMs = System.currentTimeMillis();

        for (PreExecutionGuard guard : preExecutionGuards) {
            PreExecutionGuard.GuardResult r = guard.check(automationId, plan, state, firingDeviceId, nowMs);
            state = r.updatedState();
            if (!r.proceed()) {
                // preserve the old "coalition skip still persists member-fired timestamp" behavior
                stateStore.forceWrite(automationId, state);
                publishSkippedLog(automationId, plan, user, payload, r.skipReason(), traceId, r.status());
                return;
            }
        }
        // ── Evaluate ───────────────────────────────────────────────────
        EvalAttempt attempt0;
        try {
            attempt0 = evaluateOnce(plan, payload, state, automationId, traceId);
            log.debug("Automation {} final result getConditionResults={} getActionsToFire={} outcome={}",
                    plan.getAutomationName(),
                    attempt0.result().getConditionResults(),
                    attempt0.result().getActionsToFire(),
                    attempt0.result().getOutcome());
        } catch (Exception e) {
            log.error("❌ [traceId={}] Evaluation failed: {}", traceId, e.getMessage(), e);
            publishSkippedLog(automationId, plan, user, payload,
                    "Evaluation error: " + e.getMessage(), traceId, AutomationLog.LogStatus.ERROR);
            return;
        }

        EvalResult result = attempt0.result();
        log.debug("📋 [traceId={}] outcome={} c1={} anyWasActive={}",
                traceId, result.getOutcome(), result.isC1True(), result.isAnyWasActive());

        // ── No state change — persist memory + snapshot ────────────────
        if (!attempt0.hasChanges()) {
            writePostCasMemoryUpdates(automationId, result, state);
            evalSnapshotWriter.write(automationId, plan, result, state);
            livePublisher.publish(plan, result, state, payload);
            publishLog(automationId, plan, user, payload, result);
            return;
        }

        // ── Compute next state ─────────────────────────────────────────
        AutomationRuntimeState nextState = computeNextState(state, result, plan);

        // ── CAS write with retry ───────────────────────────────────────
        boolean written = false;
        for (int attempt = 0; attempt < CAS_MAX_RETRIES && !written; attempt++) {
            if (attempt > 0) {
                log.debug("🔄 [traceId={}] CAS retry attempt {}", traceId, attempt);
                try {
                    state = stateStore.read(automationId);
                    EvalAttempt retryAttempt = evaluateOnce(plan, payload, state, automationId, traceId);
                    result = retryAttempt.result();
                    if (!retryAttempt.hasChanges()) {
                        writePostCasMemoryUpdates(automationId, result, state);
                        evalSnapshotWriter.write(automationId, plan, result, state);
                        publishLog(automationId, plan, user, payload, result);
                        return;
                    }
                    nextState = computeNextState(state, result, plan);
                } catch (Exception e) {
                    log.error("❌ [traceId={}] Evaluation failed on retry {}: {}",
                            traceId, attempt, e.getMessage(), e);
                    publishSkippedLog(automationId, plan, user, payload,
                            "Evaluation error on retry: " + e.getMessage(), traceId, AutomationLog.LogStatus.ERROR);
                    return;
                }
            }
            written = stateStore.compareAndSet(automationId, state.getVersion(), nextState);
        }

        if (!written) {
            log.warn("⚡ [traceId={}] CAS conflict unresolved after {} attempts", traceId, CAS_MAX_RETRIES);
            publishSkippedLog(automationId, plan, user, payload,
                    "CAS conflict — concurrent state update", traceId, AutomationLog.LogStatus.ERROR);
            return;
        }

        // ── Post-CAS ───────────────────────────────────────────────────
        if (outcomeHandlers.get(result.getOutcome()).armsScheduleKeys()) {
            writePostCasScheduleKeys(result, automationId, plan);
        }
        evalSnapshotWriter.write(automationId, plan, result, nextState);
        livePublisher.publish(plan, result, nextState, payload);

        if (outcomeHandlers.get(result.getOutcome()).persistsSnapshot()) {
            AutomationStateSnapshot snap = AutomationStateSnapshot.from(automationId, nextState, Instant.now());
            CompletableFuture.runAsync(() -> {
                try {
                    stateSnapshotRepository.save(snap);
                } catch (Exception e) {
                    log.warn("⚠️ Failed to persist state snapshot for '{}': {}", automationId, e.getMessage());
                }
            });
        }
        dispatchResult(result, plan, payload, user, automationId);
    }

    private record EvalAttempt(EvalResult result, boolean hasChanges) {
    }

    private EvalAttempt evaluateOnce(
            ExecutionPlan plan, Map<String, Object> payload,
            AutomationRuntimeState state, String automationId, String traceId
    ) {
        EvalResult result = evaluator.evaluate(plan, payload, state, automationId, traceId);
        if (result.getOutcome() == EvalOutcome.C1_NEGATIVE) {
            result = strandedActionResolver.resolve(result, plan, state, automationId);
        }
        boolean hasChanges = outcomeHandlers.get(result.getOutcome()).hasChanges();
        return new EvalAttempt(result, hasChanges);
    }
    // ─────────────────────────────────────────────────────────────────────
    // MEMORY UPDATE POST-CAS
    // ─────────────────────────────────────────────────────────────────────

    private void writePostCasMemoryUpdates(
            String automationId,
            EvalResult result,
            AutomationRuntimeState current
    ) {
        Map<String, ConditionMemory> updates = result.getMemoryUpdates();
        if (updates == null || updates.isEmpty()) return;
        // Use the state already in hand — don't re-read from Redis/MongoDB
        updates.forEach(current::setConditionMemory);
        stateStore.forceWrite(automationId, current);
    }


    // ─────────────────────────────────────────────────────────────────────
    // POST-CAS SCHEDULE KEYS
    // ─────────────────────────────────────────────────────────────────────

    private void writePostCasScheduleKeys(
            EvalResult result,
            String automationId,
            ExecutionPlan plan
    ) {
        // Write schedule keys for both TRIGGERED and BRANCH_TRIGGERED outcomes —
        // both represent a successful positive dispatch that arms cooldown timers.

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
    private AutomationRuntimeState computeNextState(
            AutomationRuntimeState current,
            EvalResult result,
            ExecutionPlan plan
    ) {
        AutomationRuntimeState next = current.withNextVersion();

        if (result.getMemoryUpdates() != null) {
            result.getMemoryUpdates().forEach(next::setConditionMemory);
        }

        log.debug("[{}] computeNextState before {}", plan.getAutomationName(), next.getTopLevelState());
        outcomeHandlers.get(result.getOutcome()).applyStateTransition(next, result, plan);
        log.debug("[{}] computeNextState after {}", plan.getAutomationName(), next.getTopLevelState());
        return next;
    }

    private void dispatchResult(
            EvalResult result,
            ExecutionPlan plan,
            Map<String, Object> payload,
            String user,
            String automationId
    ) {
        String name = plan.getAutomationName();

        DispatchContext ctx = new DispatchContext(
                dispatcher, stateStore, planCache, notificationService,
                automationId, name, result.getTraceId(), plan.getHomeId(),
                payload, user,
                () -> publishLog(automationId, plan, user, payload, result)
        );

        outcomeHandlers.get(result.getOutcome()).dispatch(result, plan, ctx);
    }


    // ─────────────────────────────────────────────────────────────────────
    // LOGGING
    // ─────────────────────────────────────────────────────────────────────

    private void publishLog(
            String automationId, ExecutionPlan plan,
            String user, Map<String, Object> payload,
            EvalResult result
    ) {
        AutomationLog.LogStatus status = outcomeHandlers.get(result.getOutcome()).logStatus();

        logStream.publish(AutomationLog.builder()
                .automationId(automationId)
                .automationName(plan.getAutomationName())
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

    private void publishSkippedLog(
            String automationId, ExecutionPlan plan,
            String user, Map<String, Object> payload,
            String reason, String traceId,
            AutomationLog.LogStatus status
    ) {
        if (plan == null) {
            log.error("Plan not found automationId={} traceId={}", automationId, traceId);
            throw new RuntimeException("Plan not found");
        }

        logStream.publish(AutomationLog.builder()
                .automationId(automationId)
                .automationName(plan.getAutomationName())
                .user(user)
                .triggerDeviceId(plan.getTriggerDeviceId())
                .timestamp(new Date())
                .payload(payload != null ? payload : Map.of())
                .status(status)
                .reason(reason)
                .traceId(traceId)
                .build());
    }

}