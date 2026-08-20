package dev.automata.automata.automation_engine;

import dev.automata.automata.automation_engine.enums.EvalOutcome;
import dev.automata.automata.automation_engine.evaluator.DispatchContext;
import dev.automata.automata.automation_engine.evaluator.OutcomeHandlerRegistry;
import dev.automata.automata.automation_engine.helpers.ActionResolver;
import dev.automata.automata.dto.AutomationRuntimeState;
import dev.automata.automata.dto.ConditionMemory;
import dev.automata.automata.model.Automation;
import dev.automata.automata.model.AutomationLog;
import dev.automata.automata.model.AutomationStateSnapshot;
import dev.automata.automata.repository.AutomationRepository;
import dev.automata.automata.repository.AutomationStateSnapshotRepository;
import dev.automata.automata.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static dev.automata.automata.automation_engine.enums.EvalOutcome.*;

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
    private final AutomationLivePublisher livePublisher;
    private final CoalitionGuard coalitionGuard;
    private final AutomationStateSnapshotRepository stateSnapshotRepository;
    private final OutcomeHandlerRegistry outcomeHandlers;
    private final PlanReconciliationService planReconciliationService;
    private final ActionResolver actionResolver;
    private final EvalSnapshotWriter evalSnapshotWriter;

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int CAS_MAX_RETRIES = 2;
    public static final String PLAN_INVALIDATE_CHANNEL = "automation:plan:invalidated";

    private final ConcurrentHashMap<String, String> nameCache = new ConcurrentHashMap<>();


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

        // ── Load plan ──────────────────────────────────────────────────
        Optional<ExecutionPlan> maybePlan = planReconciliationService.ensureFresh(automationId, traceId);
        if (maybePlan.isEmpty()) return; // already logged inside ensureFresh()
        ExecutionPlan plan = maybePlan.get();

        // ── 2. Snooze / timed-disable ─────────────────────────────────────
        if (stateStore.isSnoozed(automationId)) {
            long rem = Optional.ofNullable(stateStore.snoozeTTL(automationId)).orElse(0L);
            publishSkippedLog(automationId, plan, user, payload,
                    "Snoozed — " + rem / 60 + "min remaining", traceId, AutomationLog.LogStatus.SUPPRESSED);
            return;
        }
        if (stateStore.isTimedDisabled(automationId)) {
            long rem = Optional.ofNullable(stateStore.timedDisableTTL(automationId)).orElse(0L);
            publishSkippedLog(automationId, plan, user, payload,
                    "Timed-disabled — " + rem / 60 + "min remaining", traceId, AutomationLog.LogStatus.SUPPRESSED);
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
                        traceId, AutomationLog.LogStatus.NOT_MET);
                return;
            }

            state = stateWithMember;

            if (plan.getTriggerCoalition().getMode() == TriggerCoalition.CoalitionMode.SEQUENCE) {
                handleSequenceProgress(plan.getTriggerCoalition(), firingDeviceId, state,
                        coalitionResult.status(), nowMs);
            }
        }

        // ── 5. Evaluate ───────────────────────────────────────────────────
        EvalResult result;
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
                    "Evaluation error: " + e.getMessage(), traceId, AutomationLog.LogStatus.ERROR);
            return;
        }

        // BUG 1 fix: fold in negative actions for stranded descendants
        if (result.getOutcome() == EvalOutcome.C1_NEGATIVE) {
            result = foldInStrandedNegativeActions(result, plan, state, automationId);
        }

        log.debug("📋 [traceId={}] outcome={} c1={} anyWasActive={}",
                traceId, result.getOutcome(), result.isC1True(), result.isAnyWasActive());

        // ── 6. No state change — persist memory + snapshot ────────────────
        if (!outcomeHandlers.get(result.getOutcome()).hasChanges()) {
            writePostCasMemoryUpdates(automationId, result, state);
            evalSnapshotWriter.write(automationId, plan, result, state);
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
                    if (result.getOutcome() == EvalOutcome.C1_NEGATIVE) {
                        result = foldInStrandedNegativeActions(result, plan, state, automationId);
                    }
                    if (!outcomeHandlers.get(result.getOutcome()).hasChanges()) {
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

        // ── 9. Post-CAS ───────────────────────────────────────────────────
        writePostCasScheduleKeys(result, automationId, plan);
        evalSnapshotWriter.write(automationId, plan, result, nextState);
        livePublisher.publish(plan, result, nextState, payload);

        if (result.getOutcome() == TRIGGERED
                || result.getOutcome() == BRANCH_TRIGGERED
                || result.getOutcome() == C1_NEGATIVE) {
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


    // ─────────────────────────────────────────────────────────────────────
    // BUG 1 FIX — STRANDED DESCENDANT NEGATIVE ACTIONS
    // ─────────────────────────────────────────────────────────────────────

    /**
     * True if some other node in {@code strandedIds} is reachable from {@code node}
     * by following positiveChildNodeIds — i.e. a deeper, more specific stranded
     * node exists further down this node's chain. Used by RULE A so an ancestor's
     * own negativeActions are suppressed in favor of the deepest active descendant.
     */
    private boolean hasStrandedDescendant(ExecutionPlan.CompiledConditionNode node,
                                          Map<String, ExecutionPlan.CompiledConditionNode> nodeById,
                                          Set<String> strandedIds,
                                          Set<String> visited) {
        if (node.getPositiveChildNodeIds() == null || !visited.add(node.getNodeId())) return false;
        for (String childId : node.getPositiveChildNodeIds()) {
            if (strandedIds.contains(childId)) return true;
            ExecutionPlan.CompiledConditionNode child = nodeById.get(childId);
            if (child != null && hasStrandedDescendant(child, nodeById, strandedIds, visited)) return true;
        }
        return false;
    }

    private EvalResult foldInStrandedNegativeActions(
            EvalResult result,
            ExecutionPlan plan,
            AutomationRuntimeState prevState,
            String automationId) {

        if (plan.getConditionTree() == null || plan.getConditionTree().isEmpty()) return result;

        Map<String, Boolean> walkedThisTick =
                result.getConditionResults() != null ? result.getConditionResults() : Map.of();

        List<ExecutionPlan.CompiledAction> existing =
                result.getActionsToFire() != null ? result.getActionsToFire() : List.of();

        // RULE B: resolve by deviceId+key, NOT deviceId+key+data. Two stranded
        // nodes firing different values for the same device/key (e.g. bright=5
        // from one ancestor and bright=1 from another) used to both slip through
        // because the old key included the value itself. "existing" (this tick's
        // freshly-walked result) is always authoritative and seeds the map first.
        List<ExecutionPlan.CompiledAction> candidates = new ArrayList<>(existing);

        // RULE A applied to stranding: build the id → node map once so we can tell,
        // for two stranded nodes on the same chain, which one is the deeper/more
        // specific descendant. Only the deepest stranded node in a chain fires its
        // own negativeActions — an ancestor's "dim" action must not stack on top
        // of a descendant's "off" action just because both happened to be ACTIVE
        // when their common root failed.
        Map<String, ExecutionPlan.CompiledConditionNode> nodeById = plan.getConditionTree().stream()
                .collect(Collectors.toMap(ExecutionPlan.CompiledConditionNode::getNodeId, n -> n));

        List<ExecutionPlan.CompiledConditionNode> strandedCandidates = new ArrayList<>();
        Map<String, Boolean> extendedResults = new LinkedHashMap<>(walkedThisTick);
        long nowMs = System.currentTimeMillis();

        for (ExecutionPlan.CompiledConditionNode node : plan.getConditionTree()) {
            if (!node.isStateful()) continue;
            boolean wasActive = prevState.isNodeActive(node.getNodeId());
            boolean walkedThisNode = walkedThisTick.containsKey(node.getNodeId());
            if (wasActive && !walkedThisNode) strandedCandidates.add(node);
        }
        Set<String> strandedIds = strandedCandidates.stream()
                .map(ExecutionPlan.CompiledConditionNode::getNodeId)
                .collect(Collectors.toSet());

        // A stranded node is "superseded" if any other stranded node is reachable
        // from it via positiveChildNodeIds — i.e. a deeper stranded node exists
        // further down the same chain.
        Set<String> superseded = new HashSet<>();
        for (ExecutionPlan.CompiledConditionNode node : strandedCandidates) {
            if (hasStrandedDescendant(node, nodeById, strandedIds, new HashSet<>())) {
                superseded.add(node.getNodeId());
            }
        }

        for (ExecutionPlan.CompiledConditionNode node : strandedCandidates) {
            boolean walkedThisNode = walkedThisTick.containsKey(node.getNodeId());
            if (walkedThisNode) continue; // not stranded

            ExecutionPlan.CompiledCondition c = node.getCondition();
            boolean hasGrace = c != null
                    && c.getDurationMinutes() > 0
                    && !"scheduled".equals(c.getConditionType());

            if (hasGrace) {
                long durationMs = c.getDurationMinutes() * 1000L;
                Long armedAt = stateStore.getGraceArmedAtEpochMs(automationId, node.getNodeId());

                if (armedAt == null) {
                    // Parent just failed this tick, stranding this child for the first time.
                    // Start its grace clock and keep it ACTIVE — don't fire negatives yet,
                    // and don't mark it IDLE in extendedResults (leaving it unset means
                    // applyPerNodeActiveFlags() leaves the node's state untouched = still ACTIVE).
                    stateStore.armGrace(automationId, node.getNodeId(), nowMs,
                            c.getDurationMinutes() * 5L);
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

            extendedResults.put(node.getNodeId(), false);

            if (superseded.contains(node.getNodeId())) {
                // RULE A: a deeper stranded descendant already speaks for this
                // device/branch — this ancestor's own negativeActions are stale
                // and must not be dispatched alongside the descendant's.
                log.debug("🧩 Stranded node '{}' superseded by a deeper stranded descendant — "
                        + "its own negative actions are skipped", node.getNodeId());
                continue;
            }

            log.debug("🧩 Stranded descendant '{}' was active but not walked this tick — "
                            + "firing its {} negative action(s)",
                    node.getNodeId(), node.getNegativeActions() != null
                            ? node.getNegativeActions().size() : 0);

            if (node.getNegativeActions() != null) {
                candidates.addAll(node.getNegativeActions());
            }
        }

        if (candidates.size() == existing.size() && extendedResults.size() == walkedThisTick.size()) return result;

        List<ExecutionPlan.CompiledAction> combined = new ArrayList<>(actionResolver.resolve(candidates));

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

    private void writePostCasScheduleKeys(EvalResult result,
                                          String automationId,
                                          ExecutionPlan plan) {
        // Write schedule keys for both TRIGGERED and BRANCH_TRIGGERED outcomes —
        // both represent a successful positive dispatch that arms cooldown timers.
        if (result.getOutcome() != EvalOutcome.TRIGGERED
                && result.getOutcome() != EvalOutcome.BRANCH_TRIGGERED) return;
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
                                                    EvalResult result,
                                                    ExecutionPlan plan) {
        AutomationRuntimeState next = current.withNextVersion();

        if (result.getMemoryUpdates() != null) {
            result.getMemoryUpdates().forEach(next::setConditionMemory);
        }

        log.debug("[{}] computeNextState before {}", plan.getAutomationName(), next.getTopLevelState());
        outcomeHandlers.get(result.getOutcome()).applyStateTransition(next, result, plan);
        log.debug("[{}] computeNextState after {}", plan.getAutomationName(), next.getTopLevelState());
        return next;
    }

    private void dispatchResult(EvalResult result,
                                ExecutionPlan plan,
                                Map<String, Object> payload,
                                String user,
                                String automationId) {
        String name = resolveAutomationName(automationId);

        DispatchContext ctx = new DispatchContext(
                dispatcher, stateStore, planCache, notificationService,
                automationId, name, result.getTraceId(), plan.getHomeId(),
                payload, user,
                () -> publishLog(automationId, plan, user, payload, result)
        );

        outcomeHandlers.get(result.getOutcome()).dispatch(result, plan, ctx);
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
        stateStore.writePlan(automationId, plan);
        redisTemplate.convertAndSend(PLAN_INVALIDATE_CHANNEL, automationId);
        log.info("📡 Plan updated and invalidation published for '{}'", automationId);
    }


    // ─────────────────────────────────────────────────────────────────────
    // LOGGING
    // ─────────────────────────────────────────────────────────────────────

    private void publishLog(String automationId, ExecutionPlan plan,
                            String user, Map<String, Object> payload,
                            EvalResult result) {
        AutomationLog.LogStatus status = outcomeHandlers.get(result.getOutcome()).logStatus();

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
                                   String reason, String traceId,
                                   AutomationLog.LogStatus status) {
        logStream.publish(AutomationLog.builder()
                .automationId(automationId)
                .automationName(resolveAutomationName(automationId))
                .user(user)
                .triggerDeviceId(plan != null ? plan.getTriggerDeviceId() : "")
                .timestamp(new Date())
                .payload(payload != null ? payload : Map.of())
                .status(status)
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