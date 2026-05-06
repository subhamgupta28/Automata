package dev.automata.automata.v2;

import dev.automata.automata.dto.AutomationRuntimeState;
import dev.automata.automata.dto.BranchDecision;
import dev.automata.automata.model.Automation;
import dev.automata.automata.model.AutomationLog;
import dev.automata.automata.repository.AutomationRepository;
import dev.automata.automata.service.NotificationService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

/**
 * Core automation orchestrator.
 * <p>
 * Responsibilities per execution:
 * 1. Load pre-compiled ExecutionPlan from PlanCache (local JVM map, no IO)
 * 2. Check snooze/timed-disable (two Redis EXISTS — read-only, no lock)
 * 3. Read AutomationRuntimeState (one Redis GET, ~200 bytes)
 * 4. Evaluate via AutomationEvaluator (pure, no side effects)
 * 5. Compute next state from EvalResult (pure)
 * 6. CAS write next state (Lua script, retries once on conflict)
 * 7. Dispatch actions via ActionDispatcher
 * 8. Write schedule keys after confirmed dispatch
 * 9. Publish log entry to AutomationLogStream
 * <p>
 * Fixes vs previous version:
 * Bug 4/5: Redis pub/sub now uses RedisMessageListenerContainer (correct
 * Lettuce pattern) instead of raw getConnection().subscribe() which
 * permanently took a connection out of the pool.
 * Bug 7:   Log is now published INSIDE the dispatch CompletableFuture chain
 * so the log status reflects the actual dispatch outcome.
 * Bug 3:   No-branch TRIGGERED path correctly dispatches topLevelPositiveActions.
 * Bug 6:   execute() uses @Async("automationExecutor") which is now a real
 * Spring bean defined in AsyncConfig.
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
    private final RedisMessageListenerContainer listenerContainer;

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int CAS_MAX_RETRIES = 2;
    private static final String PLAN_INVALIDATE_CHANNEL = "automation:plan:invalidated";

    // Automation name cache — avoids a MongoDB round-trip on every log entry
    private final ConcurrentHashMap<String, String> nameCache = new ConcurrentHashMap<>();


    // ─────────────────────────────────────────────────────────────────────
    // STARTUP
    // ─────────────────────────────────────────────────────────────────────

    @PostConstruct
    public void reconcile() {
        log.info("🔄 AutomationOrchestrator startup reconciliation...");

        // 1. Warm plan cache from MongoDB
        planCache.warmAll();

        // 2. Log any automations that have no compiled plan (need re-saving)
        automationRepository.findEnabledForExecution().forEach(a -> {
            if (planCache.get(a.getId()) == null)
                log.warn("⚠️ No compiled plan for '{}' (id={}) — open and re-save in editor",
                        a.getName(), a.getId());
        });

        // 3. Subscribe to plan invalidation via RedisMessageListenerContainer.
        //    Bug fix: was using raw getConnection().subscribe() which permanently
        //    consumed a Lettuce connection from the pool and ran the callback on
        //    the Lettuce IO thread (exceptions swallowed, no reconnect on failure).
        //    RedisMessageListenerContainer manages its own dedicated connection,
        //    reconnects automatically, and runs callbacks on a configurable executor.
        listenerContainer.addMessageListener(
                (MessageListener) (message, pattern) -> {
                    String automationId = new String(message.getBody());
                    planCache.evict(automationId);
                    nameCache.remove(automationId);
                    log.debug("📡 Plan evicted via pub/sub: '{}'", automationId);
                },
                new ChannelTopic(PLAN_INVALIDATE_CHANNEL));

        log.info("✅ Reconciliation complete — plan cache warmed, pub/sub subscribed");
    }


    // ─────────────────────────────────────────────────────────────────────
    // MAIN ENTRY POINT
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Evaluate and execute a single automation against the given payload.
     * <p>
     * Called from:
     * AutomationService.handleAction()             — live device event
     * AutomationService.triggerPeriodicAutomations() — 12s scheduler tick
     *
     * @Async("automationExecutor") — runs on the bounded executor defined in
     * AsyncConfig. DiscardOldestPolicy drops the oldest pending evaluation
     * if the queue is full (the dropped tick is retried on the next 12s cycle).
     */
    @Async("automationExecutor")
    public void execute(String automationId, Map<String, Object> payload, String user) {
        String traceId = automationId.substring(0, Math.min(8, automationId.length()))
                + "-" + System.currentTimeMillis()
                + "-" + Integer.toHexString((int) (Math.random() * 0xFFFF));

        log.debug("🔍 [traceId={}] execute start — automation='{}' user='{}'",
                traceId, automationId, user);

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

        // 4. Evaluate
        AutomationEvaluator.EvalResult result;
        try {
            result = evaluator.evaluate(plan, payload, state, automationId, traceId);
        } catch (Exception e) {
            log.error("❌ [traceId={}] Evaluation failed: {}", traceId, e.getMessage(), e);
            publishSkippedLog(automationId, plan, user, payload,
                    "Evaluation error: " + e.getMessage(), traceId);
            return;
        }

        // 5. No state change
        if (!result.hasChanges()) {
            publishLog(automationId, plan, user, payload, result);
            return;
        }

        // 6. Compute next state
        AutomationRuntimeState nextState = computeNextState(state, result, plan);

        // 7. CAS write with retry
        boolean written = false;
        for (int attempt = 0; attempt < CAS_MAX_RETRIES && !written; attempt++) {
            if (attempt > 0) {
                try {
                    state = stateStore.read(automationId);
                    result = evaluator.evaluate(plan, payload, state, automationId, traceId);
                    if (!result.hasChanges()) {
                        publishLog(automationId, plan, user, payload, result);
                        return;
                    }
                    nextState = computeNextState(state, result, plan);
                } catch (Exception e) {
                    log.error("❌ [traceId={}] Evaluation failed on retry attempt {}: {}",
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

        // 8. Dispatch
        dispatchResult(result, plan, payload, user, automationId, state);
    }


    // ─────────────────────────────────────────────────────────────────────
    // STATE COMPUTATION (pure)
    // ─────────────────────────────────────────────────────────────────────

    private AutomationRuntimeState computeNextState(AutomationRuntimeState current,
                                                    AutomationEvaluator.EvalResult result,
                                                    ExecutionPlan plan) {
        AutomationRuntimeState next = current.withNextVersion();

        switch (result.getOutcome()) {
            case TRIGGERED -> {
                if (!plan.hasBranches()) {
                    String nextLevelState = result.getNextTopLevelState();
                    next.setTopLevelState(nextLevelState != null ? nextLevelState : "ACTIVE");
                    next.setLastExecutionTime(new Date());
                } else {
                    applyBranchDecisions(result, next);
                }
            }
            case RESTORED -> {
                // Pure-revert tick: one or more branches ended (RUNNING key expired or
                // gate turned false) with no new trigger winning. Apply the REVERT /
                // DURATION_EXPIRED decisions from branchDecisions into the state —
                // those branches go IDLE. Branches without a decision stay unchanged.
                applyBranchDecisions(result, next);
            }
            case C1_NEGATIVE -> {
                if (plan.hasBranches()) {
                    next.getBranchStates().replaceAll((k, v) -> "IDLE");
                }
                next.setTopLevelState("IDLE");
            }
            default -> {
                // SKIPPED, NOT_MET, FALLBACK, STATELESS_FIRE — no state change
            }
        }
        return next;
    }

    private void applyBranchDecisions(AutomationEvaluator.EvalResult result,
                                      AutomationRuntimeState next) {
        if (result.getBranchDecisions() == null) return;
        for (BranchDecision d : result.getBranchDecisions()) {
            String gateNodeId = d.getBranch().getGateNodeId();
            switch (d.getType()) {
                case TRIGGER -> {
                    boolean hasDuration = d.getBranch().hasDuration();
                    next.setBranchState(gateNodeId, hasDuration ? "HOLDING" : "ACTIVE");
                    next.setLastExecutionTime(new Date());
                }
                case REVERT, DURATION_EXPIRED -> next.setBranchState(gateNodeId, "IDLE");
                default -> { /* KEEP_ACTIVE, SUPPRESSED, COOLDOWN — no change */ }
            }
        }
    }


    // ─────────────────────────────────────────────────────────────────────
    // DISPATCH (side effects, runs after CAS write)
    // ─────────────────────────────────────────────────────────────────────

    private void dispatchResult(AutomationEvaluator.EvalResult result,
                                ExecutionPlan plan,
                                Map<String, Object> payload,
                                String user,
                                String automationId,
                                AutomationRuntimeState prevState) {
        String name = resolveAutomationName(automationId);

        switch (result.getOutcome()) {

            case STATELESS_FIRE -> {
                dispatcher.dispatch(result.getActionsToFire(), payload, user, automationId, name, result.getTraceId())
                        .thenRun(() -> publishLog(automationId, plan, user, payload, result));
            }

            case TRIGGERED -> {
                if (!plan.hasBranches()) {
                    List<ExecutionPlan.CompiledAction> actions =
                            plan.getTopLevelPositiveActions() != null
                                    ? plan.getTopLevelPositiveActions() : List.of();
                    dispatcher.dispatch(actions, payload, user, automationId, name, result.getTraceId()) // ← traceId
                            .thenRun(() -> {
                                dispatcher.notifyTriggered(name);
                                publishLog(automationId, plan, user, payload, result);
                            });
                } else {
                    dispatchBranchDecisions(result, plan, payload, user, automationId, name);
                }
            }

            case RESTORED -> {
                // Pure-revert tick: RUNNING key expired (or gate turned false with no new
                // winner). The evaluator produced REVERT/DURATION_EXPIRED decisions but no
                // TRIGGER. Route through dispatchBranchDecisions so the REVERT case fires
                // negativeActions and deletes the running key.
                // Previously fell through to default → publishLog only → negative actions
                // were never dispatched and the branch stayed HOLDING forever.
                dispatchBranchDecisions(result, plan, payload, user, automationId, name);
            }

            case C1_NEGATIVE -> {
                dispatcher.dispatch(result.getActionsToFire(), payload, user, automationId, name, result.getTraceId())
                        .thenRun(() -> {
                            if (plan.hasBranches()) {
                                plan.getBranches().stream()
                                        .filter(b -> prevState.isBranchActive(b.getGateNodeId()))
                                        .forEach(b -> stateStore.deleteRunningKey(
                                                automationId, b.getGateNodeId()));
                            }
                            notificationService.sendNotification(
                                    name + " — trigger condition lost", "info");
                            publishLog(automationId, plan, user, payload, result);
                        });
            }

            case FALLBACK -> {
                dispatcher.dispatch(result.getActionsToFire(), payload, user, automationId, name, result.getTraceId())
                        .thenRun(() -> publishLog(automationId, plan, user, payload, result));
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

        List<CompletableFuture<Void>> revertFutures = new ArrayList<>();
        List<CompletableFuture<Void>> triggerFutures = new ArrayList<>();

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
                                            log.warn("⚠️ [{}] '{}' positive actions failed",
                                                    automationName, branchDesc);
                                            return;
                                        }
                                        if (branch.hasDuration()) {
                                            stateStore.setRunningKey(automationId, gateNodeId,
                                                    branch.getGateCondition().getDurationMinutes() * 60L);
                                            log.info("⏱️ [{}] '{}' active for {}min",
                                                    automationName, branchDesc,
                                                    branch.getGateCondition().getDurationMinutes());
                                        }
                                        if ("interval".equals(
                                                branch.getGateCondition().getScheduleType())) {
                                            stateStore.setIntervalKey(automationId, gateNodeId,
                                                    branch.getGateCondition().getIntervalMinutes() * 60L);
                                        }
                                        writeDailyKeysIfNeeded(automationId,
                                                branch.getGateCondition());
                                        dispatcher.notifyTriggered(automationName);
                                        log.info("🚀 [{}] '{}' triggered (priority {})",
                                                automationName, branchDesc, branch.getPriority());
                                    });
                    triggerFutures.add(f);
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
                    revertFutures.add(f);
                }

                case SUPPRESSED -> log.debug("⏸️ [{}] '{}' suppressed — {}",
                        automationName, branchDesc, decision.getReason());

                default -> { /* KEEP_ACTIVE, COOLDOWN */ }
            }
        }

        // FIX: Add 5-second timeout to prevent cascading hangs
        try (ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1)) {

            CompletableFuture.allOf(revertFutures.toArray(new CompletableFuture[0]))
                    .thenCompose(v -> CompletableFuture.allOf(
                            triggerFutures.toArray(new CompletableFuture[0])))
                    .orTimeout(5, TimeUnit.SECONDS)  // ← Add timeout
                    .thenRun(() -> publishLog(automationId, plan, user, payload, result))
                    .exceptionally(ex -> {
                        if (ex instanceof TimeoutException) {
                            log.error("⏱️ [{}] Dispatch timeout after 5s for '{}' — " +
                                            "some actions may not have completed",
                                    automationName, automationId);
                            publishSkippedLog(automationId, plan, user, payload,
                                    "Dispatch timeout (5s) — " +
                                            (revertFutures.stream().filter(f -> !f.isDone()).count() +
                                                    triggerFutures.stream().filter(f -> !f.isDone()).count()) +
                                            " actions still pending",
                                    result.getTraceId());
                        } else {
                            log.error("❌ [{}] Dispatch error: {}", automationName, ex.getMessage(), ex);
                            publishSkippedLog(automationId, plan, user, payload,
                                    "Dispatch error: " + ex.getMessage(), result.getTraceId());
                        }
                        return null;
                    })
                    .thenRun(scheduler::shutdown);
        }
    }

    private void writeDailyKeysIfNeeded(String automationId,
                                        ExecutionPlan.CompiledCondition gc) {
        ZonedDateTime now = ZonedDateTime.now(IST);
        String today = now.toLocalDate().toString();
        long ttl = ChronoUnit.SECONDS.between(now,
                now.plusDays(1).truncatedTo(ChronoUnit.DAYS));

        if ("solar".equals(gc.getScheduleType()))
            stateStore.setDailySolarKey(automationId, today, ttl);
        else if (gc.getScheduleType() == null || "at".equals(gc.getScheduleType()))
            stateStore.setDailyFireKey(automationId, today, ttl);
    }


    // ─────────────────────────────────────────────────────────────────────
    // PLAN INVALIDATION (cross-node)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Evict local plan cache and notify all other nodes via Redis pub/sub.
     * Called from AutomationService after an automation is disabled or deleted.
     */
    public void invalidatePlan(String automationId) {
        planCache.evict(automationId);
        nameCache.remove(automationId);
        redisTemplate.convertAndSend(PLAN_INVALIDATE_CHANNEL, automationId);
        log.info("📡 Plan invalidation published for '{}'", automationId);
    }

    /**
     * Store new plan in all layers and notify other nodes.
     * Called from AutomationService after compiling a new plan on save.
     */
    public void updatePlan(String automationId, ExecutionPlan plan) {
        planCache.put(automationId, plan);
        nameCache.remove(automationId);
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
                .reason(result.getReason() != null
                        ? result.getReason() : result.getOutcome().name())
                .traceId(result.getTraceId())                    // ← NEW
                .evalDurationMs(result.getEvalDurationMs())      // ← NEW
                // deliveryStatus is null here — updated async by ActionDeliveryTracker
                .build());
    }

    private void publishSkippedLog(String automationId, ExecutionPlan plan,
                                   String user, Map<String, Object> payload,
                                   String reason,
                                   String traceId) {           // ← NEW PARAM
        logStream.publish(AutomationLog.builder()
                .automationId(automationId)
                .automationName(resolveAutomationName(automationId))
                .user(user)
                .triggerDeviceId(plan != null ? plan.getTriggerDeviceId() : "")
                .timestamp(new Date())
                .payload(payload != null ? payload : Map.of())
                .status(AutomationLog.LogStatus.SKIPPED)
                .reason(reason)
                .traceId(traceId)                               // ← NEW
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