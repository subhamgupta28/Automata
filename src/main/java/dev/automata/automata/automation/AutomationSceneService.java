package dev.automata.automata.automation;


import dev.automata.automata.model.Automation;
import dev.automata.automata.model.AutomationLog;
import dev.automata.automata.model.AutomationScene;
import dev.automata.automata.repository.AutomationRepository;
import dev.automata.automata.repository.AutomationSceneRepository;
import dev.automata.automata.service.AutomationLogBuffer;
import dev.automata.automata.service.AutomationService;
import dev.automata.automata.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationSceneService {

    private final AutomationSceneRepository sceneRepository;
    private final AutomationRepository automationRepository;
    @Lazy
    private final AutomationService automationService;
    private final NotificationService notificationService;
    private final AutomationLogBuffer logBuffer;

    private static final ScheduledExecutorService sceneScheduler =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "scene-scheduler");
                t.setDaemon(true);
                return t;
            });

    // ─────────────────────────────────────────────────────────────────────────
    // CRUD
    // ─────────────────────────────────────────────────────────────────────────

    public List<AutomationScene> findAll() {
        return sceneRepository.findAll();
    }

    public Optional<AutomationScene> findById(String id) {
        return sceneRepository.findById(id);
    }

    public AutomationScene save(AutomationScene scene) {
        scene.setUpdateDate(new Date());

        // Denormalise automation names for display
        scene.getMembers().forEach(m -> {
            if (m.getAutomationName() == null || m.getAutomationName().isBlank()) {
                automationRepository.findById(m.getAutomationId())
                        .ifPresent(a -> m.setAutomationName(a.getName()));
            }
        });

        AutomationScene saved = sceneRepository.save(scene);
        log.info("💾 Scene saved: '{}' with {} members",
                saved.getName(), saved.getMembers().size());
        return saved;
    }

    public void delete(String id) {
        sceneRepository.deleteById(id);
        log.info("🗑️ Scene deleted: {}", id);
    }

    public AutomationScene toggle(String id, boolean enabled) {
        return sceneRepository.findById(id).map(scene -> {
            scene.setIsEnabled(enabled);
            scene.setUpdateDate(new Date());
            return sceneRepository.save(scene);
        }).orElseThrow(() -> new IllegalArgumentException("Scene not found: " + id));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TRIGGER
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Triggers a scene — fires each member automation's positive actions
     * in priority order (by member.order), with configurable delays between
     * members. Members with equal order run in parallel.
     * <p>
     * The individual automation state machines are NOT advanced.
     * Scenes are always stateless fire-and-forget.
     */
    public CompletableFuture<SceneTriggerResult> trigger(String sceneId, String triggeredBy) {
        AutomationScene scene = sceneRepository.findById(sceneId)
                .orElseThrow(() -> new IllegalArgumentException("Scene not found: " + sceneId));

        if (!Boolean.TRUE.equals(scene.getIsEnabled())) {
            log.warn("Scene '{}' is disabled, skipping", scene.getName());
            return CompletableFuture.completedFuture(
                    SceneTriggerResult.failed(sceneId, scene.getName(), "Scene is disabled"));
        }

        log.info("🎬 Triggering scene '{}' by '{}'", scene.getName(), triggeredBy);
        notificationService.sendNotification(scene.getName() + " scene started", "success");

        // Group members by order — same order = parallel execution
        Map<Integer, List<AutomationScene.SceneMember>> groups = scene.getMembers().stream()
                .collect(Collectors.groupingBy(
                        AutomationScene.SceneMember::getOrder,
                        TreeMap::new,    // sorted by order key
                        Collectors.toList()
                ));

        List<SceneMemberResult> results = Collections.synchronizedList(new ArrayList<>());
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);

        for (Map.Entry<Integer, List<AutomationScene.SceneMember>> entry : groups.entrySet()) {
            List<AutomationScene.SceneMember> group = entry.getValue();

            // Execute all members in this order group in parallel
            chain = chain.thenCompose(__ -> {
                List<CompletableFuture<SceneMemberResult>> groupFutures = group.stream()
                        .map(member -> fireMember(member, triggeredBy))
                        .toList();

                return CompletableFuture.allOf(groupFutures.toArray(new CompletableFuture[0]))
                        .thenCompose(v -> {
                            groupFutures.forEach(f -> f.thenAccept(results::add));

                            // Find max delay in this group for inter-group pause
                            int maxDelay = group.stream()
                                    .mapToInt(AutomationScene.SceneMember::getDelayAfterSeconds)
                                    .max().orElse(0);

                            if (maxDelay > 0) {
                                CompletableFuture<Void> delayFuture = new CompletableFuture<>();
                                sceneScheduler.schedule(
                                        () -> delayFuture.complete(null),
                                        maxDelay, TimeUnit.SECONDS);
                                return delayFuture;
                            }
                            return CompletableFuture.completedFuture(null);
                        });
            });
        }

        return chain.thenApply(__ -> {
            scene.setLastTriggeredAt(new Date());
            sceneRepository.save(scene);
            notificationService.sendNotification(scene.getName() + " scene complete", "success");
            log.info("✅ Scene '{}' complete — {} members fired", scene.getName(), results.size());
            return SceneTriggerResult.success(sceneId, scene.getName(), results);
        }).exceptionally(ex -> {
            log.error("❌ Scene '{}' failed: {}", scene.getName(), ex.getMessage(), ex);
            notificationService.sendNotification(scene.getName() + " scene error", "error");
            return SceneTriggerResult.failed(sceneId, scene.getName(), ex.getMessage());
        });
    }

    private CompletableFuture<SceneMemberResult> fireMember(
            AutomationScene.SceneMember member, String triggeredBy) {

        return CompletableFuture.supplyAsync(() -> {
            Optional<Automation> automationOpt =
                    automationRepository.findById(member.getAutomationId());

            if (automationOpt.isEmpty()) {
                log.warn("Scene member automation not found: {}", member.getAutomationId());
                return SceneMemberResult.failed(member, "Automation not found");
            }

            Automation automation = automationOpt.get();
            if (!Boolean.TRUE.equals(automation.getIsEnabled())) {
                log.debug("Scene member '{}' is disabled, skipping", automation.getName());
                return SceneMemberResult.skipped(member, "Automation disabled");
            }

            try {
                // Execute all positive actions of this automation directly
                // (same path as user override — no state machine involvement)
                List<Automation.Action> actions = automation.getActions().stream()
                        .filter(a -> Boolean.TRUE.equals(a.getIsEnabled()))
                        .filter(a -> "positive".equalsIgnoreCase(a.getConditionGroup())
                                || "none".equalsIgnoreCase(a.getConditionGroup()))
                        .sorted(Comparator.comparingInt(
                                a -> a.getOrder() != 0 ? a.getOrder() : Integer.MAX_VALUE))
                        .toList();

                if (actions.isEmpty()) {
                    return SceneMemberResult.skipped(member, "No positive/none actions");
                }

                // Log scene execution
                AutomationLog sceneLog = AutomationLog.builder()
                        .automationId(automation.getId())
                        .automationName(automation.getName())
                        .user("scene:" + triggeredBy)
                        .triggerDeviceId(automation.getTrigger().getDeviceId())
                        .timestamp(new Date())
                        .status(AutomationLog.LogStatus.USER_OVERRIDE)
                        .reason("Fired as part of scene — " + member.getAutomationName())
                        .payload(Map.of())
                        .build();
                logBuffer.add(sceneLog);

                log.info("▶️ Scene member '{}' (order={}) firing {} action(s)",
                        automation.getName(), member.getOrder(), actions.size());

                // executeAutomationImmediate equivalent — direct fire
                automationService.executeSceneActions(
                        automation, actions, Map.of(), triggeredBy);

                return SceneMemberResult.success(member, actions.size());

            } catch (Exception e) {
                log.error("❌ Scene member '{}' failed: {}",
                        member.getAutomationName(), e.getMessage(), e);
                return SceneMemberResult.failed(member, e.getMessage());
            }
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // RESULT TYPES
    // ─────────────────────────────────────────────────────────────────────────

    public record SceneTriggerResult(
            String sceneId,
            String sceneName,
            boolean success,
            String error,
            List<SceneMemberResult> memberResults) {

        static SceneTriggerResult success(String id, String name, List<SceneMemberResult> results) {
            return new SceneTriggerResult(id, name, true, null, results);
        }

        static SceneTriggerResult failed(String id, String name, String error) {
            return new SceneTriggerResult(id, name, false, error, List.of());
        }
    }

    public record SceneMemberResult(
            String automationId,
            String automationName,
            int order,
            String status,      // "success" | "skipped" | "failed"
            int actionsRun,
            String reason) {

        static SceneMemberResult success(AutomationScene.SceneMember m, int actionsRun) {
            return new SceneMemberResult(m.getAutomationId(), m.getAutomationName(),
                    m.getOrder(), "success", actionsRun, null);
        }

        static SceneMemberResult skipped(AutomationScene.SceneMember m, String reason) {
            return new SceneMemberResult(m.getAutomationId(), m.getAutomationName(),
                    m.getOrder(), "skipped", 0, reason);
        }

        static SceneMemberResult failed(AutomationScene.SceneMember m, String reason) {
            return new SceneMemberResult(m.getAutomationId(), m.getAutomationName(),
                    m.getOrder(), "failed", 0, reason);
        }
    }
}
