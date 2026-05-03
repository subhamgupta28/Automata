package dev.automata.automata.automation;


import dev.automata.automata.dto.AutomationCache;
import dev.automata.automata.dto.AutomationState;
import dev.automata.automata.dto.ExecutionContext;
import dev.automata.automata.dto.NodeResult;
import dev.automata.automata.model.Automation;
import dev.automata.automata.model.AutomationAbTest;
import dev.automata.automata.model.AutomationAbTestLog;
import dev.automata.automata.model.AutomationLog;
import dev.automata.automata.repository.AutomationAbTestLogRepository;
import dev.automata.automata.repository.AutomationAbTestRepository;
import dev.automata.automata.repository.AutomationRepository;
import dev.automata.automata.service.NotificationService;
import dev.automata.automata.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationAbTestService {

    private final AutomationAbTestRepository abTestRepository;
    private final AutomationAbTestLogRepository abTestLogRepository;
    private final AutomationRepository automationRepository;
    private final NotificationService notificationService;
    private final AutomationEngine automationEngine;
    // FIX Bug#3: inject RedisService so we can persist and reuse a real shadow
    // cache for variant B instead of always starting with IDLE.
    private final RedisService redisService;

    /**
     * Redis key prefix for variant B shadow cache.
     * Format: AB_SHADOW:<testId>:<variantBId>
     */
    private static final String SHADOW_CACHE_PREFIX = "AB_SHADOW:";

    private final ConcurrentLinkedQueue<AutomationAbTestLog> logBuffer =
            new ConcurrentLinkedQueue<>();


    // ─────────────────────────────────────────────────────────────────────────
    // CRUD
    // ─────────────────────────────────────────────────────────────────────────

    public List<AutomationAbTest> findAll() {
        return abTestRepository.findAll();
    }

    public List<AutomationAbTest> findRunning() {
        return abTestRepository.findByStatus(AutomationAbTest.AbTestStatus.RUNNING);
    }

    /**
     * Creates a new A/B test.
     * variantAId: the currently-live automation (unchanged, will keep firing).
     * variantBId: the candidate automation (must already exist in the DB
     * with isEnabled=false — the user creates it via the editor first).
     */
    public AutomationAbTest create(AutomationAbTest test, String createdBy) {
        automationRepository.findById(test.getVariantAId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Variant A automation not found: " + test.getVariantAId()));

        Automation variantB = automationRepository.findById(test.getVariantBId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Variant B automation not found: " + test.getVariantBId()));

        if (Boolean.TRUE.equals(variantB.getIsEnabled())) {
            variantB.setIsEnabled(false);
            automationRepository.save(variantB);
            log.info("Disabled variant B '{}' — it will only run in A/B shadow mode",
                    variantB.getName());
        }

        test.setStatus(AutomationAbTest.AbTestStatus.RUNNING);
        test.setStartedAt(new Date());
        test.setStartedBy(createdBy);
        test.setStats(AutomationAbTest.AbTestStats.builder()
                .totalEvaluations(0)
                .variantATriggerCount(0)
                .variantBTriggerCount(0)
                .agreementRate(0.0)
                .recentDivergences(new ArrayList<>())
                .lastComputedAt(new Date())
                .build());

        AutomationAbTest saved = abTestRepository.save(test);
        log.info("🧪 A/B test '{}' started — A:{} vs B:{}",
                test.getName(), test.getVariantAId(), test.getVariantBId());
        notificationService.sendNotification(
                "A/B test started: " + test.getName(), "success");
        return saved;
    }

    public AutomationAbTest pause(String testId) {
        return updateStatus(testId, AutomationAbTest.AbTestStatus.PAUSED, "A/B test paused");
    }

    public AutomationAbTest resume(String testId) {
        return updateStatus(testId, AutomationAbTest.AbTestStatus.RUNNING, "A/B test resumed");
    }

    /**
     * Ends the test and records which variant won.
     * Re-enables variant B if B won.
     */
    public AutomationAbTest end(String testId, String winner, String conclusion) {
        AutomationAbTest test = abTestRepository.findById(testId)
                .orElseThrow(() -> new IllegalArgumentException("Test not found: " + testId));

        test.setStatus(AutomationAbTest.AbTestStatus.ENDED);
        test.setEndedAt(new Date());
        test.setWinnerVariant(winner);
        test.setConclusion(conclusion);

        updateStats(test);

        if ("B".equals(winner)) {
            automationRepository.findById(test.getVariantBId()).ifPresent(b -> {
                b.setIsEnabled(true);
                automationRepository.save(b);
            });
            automationRepository.findById(test.getVariantAId()).ifPresent(a -> {
                a.setIsEnabled(false);
                automationRepository.save(a);
            });
            log.info("🏆 Variant B promoted to live for test '{}'", test.getName());
            notificationService.sendNotification(
                    "A/B test '" + test.getName() + "' — Variant B promoted", "success");
        } else {
            log.info("✅ Variant A retained for test '{}'", test.getName());
            notificationService.sendNotification(
                    "A/B test '" + test.getName() + "' ended — Variant A retained", "info");
        }

        // FIX Bug#3: clean up the shadow cache for this test when it ends
        clearShadowCache(test.getId(), test.getVariantBId());

        return abTestRepository.save(test);
    }


    // ─────────────────────────────────────────────────────────────────────────
    // SHADOW EVALUATION
    // Called from AutomationService INSIDE allOf().thenRun() after variant A
    // has fully resolved — so variantAResult is the confirmed execution outcome.
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * FIX Bug#1 + Bug#2: this method is now actually called from AutomationService,
     * inside CompletableFuture.allOf().thenRun(), so variantAResult reflects the
     * true post-execution state of variant A (not a pre-future optimistic flag).
     */
    @Async
    public void shadowEvaluate(String variantAId,
                               Map<String, Object> payload,
                               boolean variantAResult,
                               List<AutomationLog.ConditionResult> variantAConditionResults,
                               String variantAWinningBranch) {

        // FIX Bug#4 (repo): findByVariantAIdAndStatus returns Optional<AutomationAbTest>.
        // Verify this method signature exists in AutomationAbTestRepository:
        //   Optional<AutomationAbTest> findByVariantAIdAndStatus(String variantAId, AbTestStatus status);
        abTestRepository.findByVariantAIdAndStatus(
                        variantAId, AutomationAbTest.AbTestStatus.RUNNING)
                .ifPresent(test -> {
                    try {
                        evaluateVariantB(test, payload, variantAResult,
                                variantAConditionResults, variantAWinningBranch);
                    } catch (Exception e) {
                        log.error("Shadow evaluation failed for test '{}': {}",
                                test.getName(), e.getMessage(), e);
                    }
                });
    }

    private void evaluateVariantB(AutomationAbTest test,
                                  Map<String, Object> payload,
                                  boolean variantAResult,
                                  List<AutomationLog.ConditionResult> variantAResults,
                                  String variantAWinningBranch) {

        Automation variantB = automationRepository.findById(test.getVariantBId())
                .orElse(null);
        if (variantB == null) return;

        // FIX Bug#3: load variant B's real shadow cache from Redis instead of always
        // using IDLE. This ensures wasActive is correct for hysteresis evaluation,
        // preventing artificial divergences at boundary sensor values.
        String shadowCacheKey = SHADOW_CACHE_PREFIX + test.getId() + ":" + test.getVariantBId();
        AutomationCache shadowCache = loadShadowCache(shadowCacheKey, variantB);

        ExecutionContext ctxB = new ExecutionContext();
        NodeResult rootB = automationEngine.evaluate(variantB, payload, ctxB, shadowCache);
        boolean variantBResult = rootB != null && rootB.isTrue();

        // Update the shadow cache state based on variant B's evaluation result
        // so the next tick has correct wasActive values for hysteresis.
        updateShadowCacheState(shadowCacheKey, shadowCache, variantB, ctxB, variantBResult);

        List<AutomationLog.ConditionResult> bResults =
                automationEngine.buildConditionResultsPublic(variantB, ctxB, payload);

        String bWinningBranch = resolveWinningBranchDescription(variantB, ctxB);

        boolean agreed = (variantAResult == variantBResult);

        AutomationAbTestLog entry = AutomationAbTestLog.builder()
                .testId(test.getId())
                .timestamp(new Date())
                .payload(payload)
                .agreed(agreed)
                .variantA(AutomationAbTestLog.VariantResult.builder()
                        .variantId(test.getVariantAId())
                        .rootTrue(variantAResult)
                        .conditionResults(variantAResults)
                        .winningBranchDescription(variantAWinningBranch)
                        .actionsExecuted(true)
                        .build())
                .variantB(AutomationAbTestLog.VariantResult.builder()
                        .variantId(test.getVariantBId())
                        .rootTrue(variantBResult)
                        .conditionResults(bResults)
                        .winningBranchDescription(bWinningBranch)
                        .actionsExecuted(false)  // ← never executes actions
                        .build())
                .build();

        logBuffer.offer(entry);

        if (!agreed) {
            log.debug("🔀 A/B divergence in test '{}' — A={} B={} payload={}",
                    test.getName(), variantAResult, variantBResult, payload);
        }
    }


    // ─────────────────────────────────────────────────────────────────────────
    // SHADOW CACHE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Loads the persisted shadow cache for variant B from Redis.
     * Falls back to a fresh IDLE cache if none exists yet.
     * TTL is 2 hours — enough to survive a restart without accumulating stale state.
     */
    private AutomationCache loadShadowCache(String key, Automation variantB) {
        try {
            AutomationCache existing = redisService.getAutomationCache(key);
            if (existing != null) return existing;
        } catch (Exception e) {
            log.warn("Failed to load shadow cache for key '{}': {}", key, e.getMessage());
        }
        return AutomationCache.builder()
                .id(variantB.getId())
                .state(AutomationState.IDLE)
                .branchStates(new HashMap<>())
                .triggeredPreviously(false)
                .previousExecutionTime(null)
                .lastUpdate(new Date())
                .build();
    }

    /**
     * After evaluating variant B in shadow mode, update the shadow cache state
     * so subsequent ticks have the correct wasActive / branch state for hysteresis.
     * <p>
     * Since variant B never actually executes actions, we advance its state based
     * purely on the evaluation result — no action confirmation needed.
     */
    private void updateShadowCacheState(String shadowCacheKey,
                                        AutomationCache shadowCache,
                                        Automation variantB,
                                        ExecutionContext ctxB,
                                        boolean variantBResult) {
        try {
            Set<String> operatorIds = variantB.getOperators() == null ? Set.of() :
                    variantB.getOperators().stream()
                    .map(Automation.Operator::getNodeId)
                    .collect(Collectors.toSet());

            boolean hasBranches = variantB.getOperators() != null
                    && !variantB.getOperators().isEmpty();

            if (hasBranches) {
                // Per-branch state: advance IDLE→ACTIVE when gate is true,
                // ACTIVE→IDLE when gate is false.
                variantB.getConditions().stream()
                        .filter(Automation.Condition::isEnabled)
                        .filter(c -> automationEngine.isGateCondition(c, operatorIds))
                        .forEach(c -> {
                            NodeResult nr = ctxB.get(c.getNodeId());
                            boolean gateTrue = nr != null && nr.isTrue();
                            AutomationState current = shadowCache.getBranchState(c.getNodeId());

                            if (gateTrue && current == AutomationState.IDLE) {
                                shadowCache.setBranchState(c.getNodeId(), AutomationState.ACTIVE);
                            } else if (!gateTrue && (current == AutomationState.ACTIVE
                                    || current == AutomationState.HOLDING)) {
                                shadowCache.setBranchState(c.getNodeId(), AutomationState.IDLE);
                            }
                        });
            } else {
                // Top-level state for no-branch automations
                AutomationState current = shadowCache.getState();
                if (variantBResult && current == AutomationState.IDLE) {
                    shadowCache.setState(AutomationState.ACTIVE);
                } else if (!variantBResult && (current == AutomationState.ACTIVE
                        || current == AutomationState.HOLDING)) {
                    shadowCache.setState(AutomationState.IDLE);
                }
            }

            shadowCache.setLastUpdate(new Date());
            // Store with 2-hour TTL — resets if the test is idle that long
            redisService.setAutomationCacheWithExpiry(shadowCacheKey, shadowCache, 7200);

        } catch (Exception e) {
            log.warn("Failed to update shadow cache '{}': {}", shadowCacheKey, e.getMessage());
        }
    }

    /**
     * Cleans up the shadow cache entries for a test when it ends.
     */
    private void clearShadowCache(String testId, String variantBId) {
        try {
            String key = SHADOW_CACHE_PREFIX + testId + ":" + variantBId;
            redisService.delete(key);
            log.info("🧹 Shadow cache cleared for test '{}'", testId);
        } catch (Exception e) {
            log.warn("Failed to clear shadow cache for test '{}': {}", testId, e.getMessage());
        }
    }


    // ─────────────────────────────────────────────────────────────────────────
    // STATS UPDATE (runs every 30 seconds for RUNNING tests)
    // ─────────────────────────────────────────────────────────────────────────

    @Scheduled(fixedDelay = 30_000)
    public void updateAllRunningTestStats() {
        abTestRepository.findByStatus(AutomationAbTest.AbTestStatus.RUNNING)
                .forEach(test -> {
                    try {
                        updateStats(test);
                        abTestRepository.save(test);
                    } catch (Exception e) {
                        log.error("Stats update failed for test '{}': {}",
                                test.getName(), e.getMessage());
                    }
                });
    }

    /**
     * FIX Bug#8: aCount and bCount now use dedicated repository count queries
     * against the full collection — not a capped 1000-entry in-memory slice.
     * agreementRate, aCount, and bCount all cover the same all-time scope.
     * <p>
     * Requires these methods in AutomationAbTestLogRepository:
     * long countByTestId(String testId);
     * long countByTestIdAndAgreedTrue(String testId);
     * long countByTestIdAndVariantA_RootTrue(String testId);
     * long countByTestIdAndVariantB_RootTrue(String testId);
     */
    private void updateStats(AutomationAbTest test) {
        long total = abTestLogRepository.countByTestId(test.getId());
        long agreed = abTestLogRepository.countByTestIdAndAgreedTrue(test.getId());
        double rate = total > 0 ? (double) agreed / total : 0.0;

        // FIX Bug#8: all-time counts via repository queries (not limited to 1000 rows)
        long aCount = abTestLogRepository.countByTestIdAndVariantATrue(test.getId());
        long bCount = abTestLogRepository.countByTestIdAndVariantBTrue(test.getId());

        // Recent divergences (up to 10) — still fetched as a limited list, which is fine
        // because this is display-only and not used in rate calculations.
        List<AutomationAbTest.DivergenceExample> divergences =
                abTestLogRepository.findByTestIdAndAgreedFalseOrderByTimestampDesc(test.getId())
                        .stream()
                        .limit(10)
                        .map(l -> AutomationAbTest.DivergenceExample.builder()
                                .timestamp(l.getTimestamp())
                                .variantAResult(l.getVariantA() != null && l.getVariantA().isRootTrue())
                                .variantBResult(l.getVariantB() != null && l.getVariantB().isRootTrue())
                                .payload(l.getPayload())
                                .variantARoot(l.getVariantA() != null
                                        ? l.getVariantA().getWinningBranchDescription() : null)
                                .variantBRoot(l.getVariantB() != null
                                        ? l.getVariantB().getWinningBranchDescription() : null)
                                .build())
                        .toList();

        test.setStats(AutomationAbTest.AbTestStats.builder()
                .totalEvaluations(total)
                .variantATriggerCount(aCount)
                .variantBTriggerCount(bCount)
                .agreementRate(rate)
                .recentDivergences(divergences)
                .lastComputedAt(new Date())
                .build());
    }


    // ─────────────────────────────────────────────────────────────────────────
    // LOG BUFFER FLUSH (every 5 seconds)
    // ─────────────────────────────────────────────────────────────────────────

    @Scheduled(fixedDelay = 5_000)
    public void flushLogBuffer() {
        if (logBuffer.isEmpty()) return;
        List<AutomationAbTestLog> batch = new ArrayList<>();
        AutomationAbTestLog entry;
        while ((entry = logBuffer.poll()) != null) batch.add(entry);
        if (!batch.isEmpty()) {
            try {
                abTestLogRepository.saveAll(batch);
                log.debug("Flushed {} A/B test log entries", batch.size());
            } catch (Exception e) {
                log.error("A/B test log flush failed: {}", e.getMessage());
                batch.forEach(logBuffer::offer); // re-queue on failure
            }
        }
    }


    // ─────────────────────────────────────────────────────────────────────────
    // QUERY
    // ─────────────────────────────────────────────────────────────────────────

    public List<AutomationAbTestLog> getRecentLogs(String testId, int limit) {
        return abTestLogRepository
                .findByTestIdOrderByTimestampDesc(testId, PageRequest.of(0, limit))
                .getContent();
    }

    public List<AutomationAbTestLog> getDivergences(String testId) {
        return abTestLogRepository.findByTestIdAndAgreedFalseOrderByTimestampDesc(testId);
    }


    // ─────────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private String resolveWinningBranchDescription(Automation automation, ExecutionContext ctx) {
        if (automation.getOperators() == null || automation.getOperators().isEmpty()) return null;

        Set<String> operatorIds = automation.getOperators().stream()
                .map(Automation.Operator::getNodeId)
                .collect(Collectors.toSet());

        return automation.getConditions().stream()
                .filter(Automation.Condition::isEnabled)
                .filter(c -> c.getPreviousNodeRef() != null &&
                        c.getPreviousNodeRef().stream()
                                .anyMatch(ref -> operatorIds.contains(ref.getNodeId())))
                .filter(c -> {
                    NodeResult nr = ctx.get(c.getNodeId());
                    return nr != null && nr.isTrue();
                })
                .max(Comparator.comparingInt(c -> {
                    String opId = c.getPreviousNodeRef().stream()
                            .map(ref -> ref.getNodeId())
                            .filter(operatorIds::contains)
                            .findFirst().orElse(null);
                    if (opId == null) return 0;
                    return automation.getOperators().stream()
                            .filter(op -> op.getNodeId().equals(opId))
                            .mapToInt(Automation.Operator::getPriority)
                            .findFirst().orElse(0);
                }))
                .map(c -> automationEngine.describeCondition(c))
                .orElse(null);
    }

    private AutomationAbTest updateStatus(String testId,
                                          AutomationAbTest.AbTestStatus status,
                                          String notification) {
        AutomationAbTest test = abTestRepository.findById(testId)
                .orElseThrow(() -> new IllegalArgumentException("Test not found: " + testId));
        test.setStatus(status);
        AutomationAbTest saved = abTestRepository.save(test);
        notificationService.sendNotification(notification + ": " + test.getName(), "info");
        return saved;
    }
}