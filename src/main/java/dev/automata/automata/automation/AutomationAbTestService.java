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

    /**
     * In-memory log buffer — flushed every 5 seconds (same pattern as AutomationLogBuffer).
     */
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
     * <p>
     * The caller provides:
     * - variantAId: the currently-live automation (unchanged, will keep firing)
     * - variantBId: the candidate automation (must already exist in the DB
     * with isEnabled=false — the user creates it via the editor first)
     */
    public AutomationAbTest create(AutomationAbTest test, String createdBy) {
        // Validate both automations exist
        automationRepository.findById(test.getVariantAId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Variant A automation not found: " + test.getVariantAId()));

        Automation variantB = automationRepository.findById(test.getVariantBId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Variant B automation not found: " + test.getVariantBId()));

        // Ensure variant B is disabled so it doesn't fire independently
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
        return updateStatus(testId, AutomationAbTest.AbTestStatus.PAUSED,
                "A/B test paused");
    }

    public AutomationAbTest resume(String testId) {
        return updateStatus(testId, AutomationAbTest.AbTestStatus.RUNNING,
                "A/B test resumed");
    }

    /**
     * Ends the test and records which variant won.
     * Re-enables variant B if B won (caller decides what to do with variant A).
     */
    public AutomationAbTest end(String testId, String winner, String conclusion) {
        AutomationAbTest test = abTestRepository.findById(testId)
                .orElseThrow(() -> new IllegalArgumentException("Test not found: " + testId));

        test.setStatus(AutomationAbTest.AbTestStatus.ENDED);
        test.setEndedAt(new Date());
        test.setWinnerVariant(winner);
        test.setConclusion(conclusion);

        // Compute final stats before closing
        updateStats(test);

        if ("B".equals(winner)) {
            // Promote variant B to live — enable it and disable variant A
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

        return abTestRepository.save(test);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SHADOW EVALUATION
    // Called from AutomationService after variant A has been evaluated and
    // its state machine has been handled. Variant B is evaluated in shadow
    // mode — same payload, no action execution.
    // ─────────────────────────────────────────────────────────────────────────

    @Async
    public void shadowEvaluate(String variantAId,
                               Map<String, Object> payload,
                               boolean variantAResult,
                               List<AutomationLog.ConditionResult> variantAConditionResults,
                               String variantAWinningBranch) {

        // Find running test for this variant A
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

        // Evaluate variant B's graph — NEVER execute actions
        ExecutionContext ctxB = new ExecutionContext();
        // Use a stub cache — variant B has no real state (always IDLE for shadow)
        AutomationCache stubCache = AutomationCache.builder()
                .state(AutomationState.IDLE)
                .build();

        NodeResult rootB = automationEngine.evaluate(variantB, payload, ctxB, stubCache);
        boolean variantBResult = rootB != null && rootB.isTrue();

        // Build variant B condition results
        List<AutomationLog.ConditionResult> bResults =
                automationEngine.buildConditionResultsPublic(variantB, ctxB, payload);

        // Determine winning branch for B (for branch automations)
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
                        .actionsExecuted(false)   // ← never executes actions
                        .build())
                .build();

        logBuffer.offer(entry);

        if (!agreed) {
            log.debug("🔀 A/B divergence in test '{}' — A={} B={} payload={}",
                    test.getName(), variantAResult, variantBResult, payload);
        }
    }

    private String resolveWinningBranchDescription(Automation automation, ExecutionContext ctx) {
        if (automation.getOperators() == null || automation.getOperators().isEmpty()) return null;
        Set<String> operatorIds = automation.getOperators().stream()
                .map(Automation.Operator::getNodeId).collect(Collectors.toSet());

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
                .map(c -> describeCondition(c))
                .orElse(null);
    }

    private String describeCondition(Automation.Condition c) {
        if ("scheduled".equals(c.getCondition())) {
            String t = c.getScheduleType();
            if ("range".equals(t)) return "Time " + c.getFromTime() + "-" + c.getToTime();
            if ("interval".equals(t)) return "Every " + c.getIntervalMinutes() + "min";
            if ("solar".equals(t)) return c.getSolarType();
            return "At " + c.getTime();
        }
        String k = c.getTriggerKey() != null ? c.getTriggerKey() : "value";
        return switch (c.getCondition()) {
            case "above" -> k + ">" + c.getValue();
            case "below" -> k + "<" + c.getValue();
            case "range" -> k + " in " + c.getAbove() + "-" + c.getBelow();
            default -> k + "=" + c.getValue();
        };
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

    private void updateStats(AutomationAbTest test) {
        long total = abTestLogRepository.countByTestId(test.getId());
        long agreed = abTestLogRepository.countByTestIdAndAgreedTrue(test.getId());
        double rate = total > 0 ? (double) agreed / total : 0.0;

        // Count per-variant triggers
        // Fetch recent logs to compute — avoid full collection scan
        List<AutomationAbTestLog> recent = abTestLogRepository
                .findByTestIdOrderByTimestampDesc(test.getId(),
                        PageRequest.of(0, 1000))
                .getContent();

        long aCount = recent.stream()
                .filter(l -> l.getVariantA() != null && l.getVariantA().isRootTrue())
                .count();
        long bCount = recent.stream()
                .filter(l -> l.getVariantB() != null && l.getVariantB().isRootTrue())
                .count();

        // Recent divergences (up to 10)
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
    // LOG BUFFER FLUSH (every 5 seconds, same pattern as AutomationLogBuffer)
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

    private AutomationAbTest updateStatus(String testId, AutomationAbTest.AbTestStatus status,
                                          String notification) {
        AutomationAbTest test = abTestRepository.findById(testId)
                .orElseThrow(() -> new IllegalArgumentException("Test not found: " + testId));
        test.setStatus(status);
        AutomationAbTest saved = abTestRepository.save(test);
        notificationService.sendNotification(notification + ": " + test.getName(), "info");
        return saved;
    }
}
