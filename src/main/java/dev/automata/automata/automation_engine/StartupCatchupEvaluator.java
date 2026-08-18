package dev.automata.automata.automation_engine;

import dev.automata.automata.dto.AutomationRuntimeState;
import dev.automata.automata.model.Automation;
import dev.automata.automata.repository.AutomationRepository;
import dev.automata.automata.service.MainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class StartupCatchupEvaluator implements ApplicationListener<ApplicationReadyEvent> {

    private final AutomationRepository automationRepository;
    private final ExecutionPlanCompiler planCompiler;
    private final AutomationStateStore stateStore;
    private final AutomationEvaluator evaluator;
    private final AutomationOrchestrator orchestrator;
    private final PlanCache planCache;
    private final MainService mainService;

    @Override
    public void onApplicationEvent(@NonNull ApplicationReadyEvent event) {
        // Run in a background thread — don't block app startup
        Thread.ofVirtual().name("startup-catchup").start(this::runCatchup);
    }

    private void runCatchup() {
        log.info("🔁 [catchup] Starting startup catch-up evaluation");
        Instant startedAt = Instant.now();

        List<Automation> automations = automationRepository.findEnabledForExecution();
        int fired = 0, skipped = 0, failed = 0;

        for (Automation automation : automations) {
            try {
                boolean didFire = catchupOne(automation);
                if (didFire) fired++;
                else skipped++;
            } catch (Exception e) {
                failed++;
                log.error("❌ [catchup] Failed for '{}': {}", automation.getName(), e.getMessage(), e);
            }
        }

        log.info("✅ [catchup] Done in {}ms — fired={}, skipped={}, failed={}",
                Duration.between(startedAt, Instant.now()).toMillis(), fired, skipped, failed);
    }

    private boolean catchupOne(Automation automation) {
        String id = automation.getId();

        // Only automations with scheduled conditions need catch-up.
        // Pure sensor automations are fine — the next sensor tick re-evaluates them.
        boolean hasSchedule = automation.getConditions() != null
                && automation.getConditions().stream()
                .anyMatch(c -> "scheduled".equals(c.getCondition()) && c.isEnabled());

        if (!hasSchedule) {
            log.debug("⏭️ [catchup] '{}' has no scheduled conditions — skipping", automation.getName());
            return false;
        }

        // Build or retrieve plan
        ExecutionPlan plan = planCache.get(id);
        if (plan == null) {
            plan = stateStore.readPlan(id);
            if (plan == null) {
                plan = planCompiler.compile(automation);
                planCache.put(id, plan);
                stateStore.writePlan(id, plan);
            } else {
                planCache.put(id, plan);
            }
        }

        // Read state — now backed by MongoDB via stateStore.read()
        AutomationRuntimeState state = stateStore.read(id);

        // Synthesize a catch-up payload using the last known trigger device data.
        // For schedule-only conditions the payload doesn't matter — evalSingleCondition
        // returns early for "scheduled" type without reading the payload. For mixed
        // automations (sensor + schedule gate), we need real device data.
        Map<String, Object> catchupPayload = buildCatchupPayload(plan, state);

        String traceId = "catchup-" + id.substring(0, Math.min(8, id.length()))
                + "-" + System.currentTimeMillis();

        EvalResult result;
        try {
            result = evaluator.evaluate(plan, catchupPayload, state, id, traceId);
        } catch (Exception e) {
            log.error("❌ [catchup] Evaluation failed for '{}': {}", automation.getName(), e.getMessage());
            return false;
        }

        if (result.hasNoChanges()) {
            log.debug("⏸️ [catchup] '{}' — no change needed (outcome={})",
                    automation.getName(), result.getOutcome());
            return false;
        }

        log.info("🔁 [catchup] '{}' — outcome={}, dispatching {} action(s)",
                automation.getName(), result.getOutcome(),
                result.getActionsToFire() != null ? result.getActionsToFire().size() : 0);

        // Route through the orchestrator's normal execute path with the synthesized payload.
        // This ensures CAS, state write, dispatch, logging all happen exactly as normal.
        orchestrator.execute(id, catchupPayload, "catchup", plan.getTriggerDeviceId(), plan.getHomeId());
        return true;
    }

    private Map<String, Object> buildCatchupPayload(ExecutionPlan plan,
                                                    AutomationRuntimeState state) {
        // Try to get the last real payload from Redis device data
        Map<String, Object> deviceData = mainService.getLastFullData(plan.getTriggerDeviceId()).getData();
        if (deviceData != null && !deviceData.isEmpty()) {
            return deviceData;
        }
        // Fall back to an empty map — scheduled conditions don't need payload values
        return Map.of();
    }
}
