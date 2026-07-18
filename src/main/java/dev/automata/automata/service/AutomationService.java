package dev.automata.automata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.automata.automata.automation_engine.*;
import dev.automata.automata.automation_extras.AutomationVersionService;
import dev.automata.automata.automation_extras.ScheduledAutomationManager;
import dev.automata.automata.dto.AutomationRuntimeState;
import dev.automata.automata.dto.LiveEvent;
import dev.automata.automata.model.*;
import dev.automata.automata.modules.Wled;
import dev.automata.automata.repository.AutomationDetailRepository;
import dev.automata.automata.repository.AutomationRepository;
import dev.automata.automata.repository.DeviceRepository;
import dev.automata.automata.repository.ExecutionPlanRepository;
import dev.automata.automata.utils.Feature;
import dev.automata.automata.utils.FeatureEnabled;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AutomationService — public API surface.
 * <p>
 * Changes vs previous version
 * ───────────────────────────
 * 1. saveAutomationDetailInternal():
 * - Operators mapping removed (operators[] no longer used by compiler).
 * - Condition mapping extended to carry positiveChildren, negativeChildren,
 * and fanoutMode from the frontend node data. These are resolved from
 * the previousNodeRef edges inside the compiler — the fields are optional
 * here and serve as a future explicit override if the frontend sends them.
 * - Schedule key cleanup iterates conditionTree nodes from the compiled plan
 * rather than automation.getConditions() to stay in sync.
 * <p>
 * 2. planHasStaleCondition() — branch check removed (no more plan.getBranches()).
 * <p>
 * 3. All other logic (handleAction, coalition routing, periodic job,
 * delete, copy, rollback) unchanged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationService {

    private final AutomationRepository automationRepository;
    private final AutomationDetailRepository automationDetailRepository;
    private final AutomationVersionService automationVersionService;
    private final ExecutionPlanCompiler planCompiler;
    private final ExecutionPlanRepository planRepository;
    private final AutomationOrchestrator orchestrator;
    private final AutomationStateStore stateStore;
    private final AutomationLogStream logStream;
    private final ActionDispatcher dispatcher;
    private final ActionDeliveryTracker deliveryTracker;
    private final PlanCache planCache;
    private final FeatureService featureService;
    private final ScheduledAutomationManager scheduledAutomationManager;
    private final MainService mainService;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final MessageChannel mqttOutboundChannel;
    private final DeviceRepository deviceRepository;
    private final RedisService redisService;
    private final ObjectMapper objectMapper;
    private final AutomationGraphValidator graphValidator;

    private static final String TOPIC_ACTION = "action/";


    // ═════════════════════════════════════════════════════════════════════
    // CRUD API
    // ═════════════════════════════════════════════════════════════════════

    public List<Automation> findAll() {
        return automationRepository.findAll();
    }

    public Automation create(Automation a) {
        return automationRepository.save(a);
    }

    public List<Automation> getActions(Users user, String homeId) {
        return automationRepository.findAll();
    }

    public AutomationDetail getAutomationDetail(String id) {
        return automationDetailRepository.findById(id).orElse(null);
    }

    public String disableAutomation(String id, Boolean enabled) {
        automationRepository.findById(id).ifPresent(a -> {
            a.setIsEnabled(enabled);
            automationRepository.save(a);
            notificationService.sendNotification("Automation updated", "success", a.getHomeId());
            orchestrator.invalidatePlan(id);
        });
        return "success";
    }


    // ═════════════════════════════════════════════════════════════════════
    // HANDLE ACTION
    // ═════════════════════════════════════════════════════════════════════

    public String handleAction(String deviceId, Map<String, Object> payload,
                               String deviceType, String user, String homeId) {

        if ("WLED".equals(deviceType)) {
            return handleWLED(deviceId, payload, user);
        }

        if ("System".equals(deviceType)) {
            String key = payload.get("key").toString();
            String data = payload.get(key).toString();
            if ("alert".equals(key)) notificationService.sendNotification("", data, homeId);
            if ("app_notify".equals(key)) notificationService.sendNotify("Automation", data, "low");
            return "success";
        }

        if ("reboot".equals(payload.get("key"))) {
            return rebootDevice(mainService.getDevice(deviceId));
        }

        if (payload.containsKey("automation")) {
            String id = payload.get(payload.get("key").toString()).toString();
            automationRepository.findById(id)
                    .ifPresent(a -> orchestrator.execute(a.getId(), new HashMap<>(), user));
            return "success";
        }

        if (payload.containsValue("master")) {
            String id = payload.get("deviceId").toString();
            int value = Integer.parseInt(payload.get("value").toString());
            String key = payload.get("key").toString();
            Map<String, Object> req = new HashMap<>();
            req.put("key", key);
            req.put(key, value);
            req.put("direct", true);
            req.put("deviceId", id);
            deviceRepository.findByIdAndHomeId(id, homeId)
                    .ifPresent(d -> handleAction(id, req, d.getType(), user, homeId));
            return "success";
        }

        if (payload.containsKey("direct")
                && Boolean.parseBoolean(payload.get("direct").toString())) {
            dispatcher.dispatchDirect(deviceId, payload);
            return "Direct action sent";
        }

        writeDeviceData(deviceId, payload);

        List<Automation> automations = findAutomationsForDevice(deviceId);
        if (automations.isEmpty()) return "No automations for device";

        automations.stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsEnabled()))
                .forEach(a -> {
                    ExecutionPlan plan = planCache.get(a.getId());
                    if (plan != null && plan.hasCoalition()) {
                        orchestrator.execute(a.getId(), payload, user, deviceId, plan.getHomeId());
                    } else {
                        orchestrator.execute(a.getId(), payload, user);
                    }
                });

        return "Action routed to " + automations.size() + " automation(s)";
    }

    private List<Automation> findAutomationsForDevice(String deviceId) {
        Set<String> seen = new LinkedHashSet<>();
        List<Automation> result = new ArrayList<>();

        automationRepository.findByTrigger_DeviceId(deviceId).stream()
                .filter(a -> seen.add(a.getId()))
                .forEach(result::add);

        try {
            automationRepository.findByCoalitionMemberDeviceId(deviceId).stream()
                    .filter(a -> seen.add(a.getId()))
                    .forEach(result::add);
        } catch (Exception e) {
            log.trace("Coalition lookup not available for device '{}': {}", deviceId, e.getMessage());
        }

        return result;
    }

    public String ackAction(String deviceId, Map<String, Object> payload) {
        if (payload.containsKey("actionAck")) {
            if (payload.containsKey("actionType"))
                notificationService.sendNotification("Action sent to device", "success", "");
            if (payload.containsKey("_cid"))
                deliveryTracker.confirm(payload.get("_cid").toString());
        }
        return "success";
    }

    public String rebootAllDevices(Users user, String homeId) {
        notificationService.sendNotification("Rebooting All Devices", "success", homeId);
        deviceRepository.findAllByHomeId(homeId).forEach(this::rebootDevice);
        notificationService.sendNotification("Reboot Complete", "success", homeId);
        return "success";
    }

    public Object sendConditionToDevice(String deviceId) {
        Map<String, Object> payload = new HashMap<>();
        StringJoiner keyJoiner = new StringJoiner(",");
        for (Automation a : automationRepository.findByTrigger_DeviceId(deviceId)) {
            List<Automation.Condition> conds = a.getConditions();
            if (conds == null || conds.isEmpty()) continue;
            Automation.Condition c = conds.get(0);
            String v = a.getTrigger().getKey();
            v += Boolean.TRUE.equals(c.getIsExact())
                    ? "=" + c.getValue()
                    : ">" + c.getAbove() + ",<" + c.getBelow();
            payload.put(a.getId(), v);
            keyJoiner.add(a.getId());
        }
        payload.put("keys", keyJoiner.toString());
        messagingTemplate.convertAndSend("/topic/action." + deviceId, Optional.of(payload));
        sendToTopic(TOPIC_ACTION + deviceId, payload);
        return payload;
    }


    // ═════════════════════════════════════════════════════════════════════
    // EVENT LISTENERS
    // ═════════════════════════════════════════════════════════════════════

    @EventListener
    public void onCustomEvent(LiveEvent event) {
        Map<String, Object> payload = event.getPayload();
        String rawId = payload.get("device_id").toString();
        String normalizedId = rawId.toLowerCase(Locale.ROOT);
        writeDeviceData(normalizedId, payload);
        if (!normalizedId.equals(rawId)) {
            writeDeviceData(rawId, payload);
        }
    }


    // ═════════════════════════════════════════════════════════════════════
    // SCHEDULED JOBS
    // ═════════════════════════════════════════════════════════════════════

    @Scheduled(fixedRate = 5_000)
    @FeatureEnabled(value = Feature.PERIODIC_AUTOMATION_SERVICE)
    public void triggerPeriodicAutomations() {
        automationRepository.findEnabledForExecution().stream()
                .filter(scheduledAutomationManager::hasAnyScheduledConditions)
                .forEach(a -> {
                    String deviceId = a.getTrigger().getDeviceId();
                    Map<String, Object> recentData = redisService.getRecentDeviceData(deviceId);
                    boolean hasRedisData = recentData != null && !recentData.isEmpty();

                    if (!hasRedisData && planHasDataDrivenTrigger(a.getId())) {

                        if (planHasStaleCondition(a.getId())) {
                            Map<String, Object> dbPayload = buildDbFallbackPayload(
                                    deviceId, a.getName());
                            log.info("🐕 [{}] Redis empty — running stale check with DB payload",
                                    a.getName());
                            prewarmSecondaryDevices(a);
                            orchestrator.execute(a.getId(), dbPayload, "scheduler");
                            return;
                        }

                        log.warn("⚠️ [{}] Skipping — no cached data for device '{}'",
                                a.getName(), deviceId);
                        return;
                    }

                    prewarmSecondaryDevices(a);
                    orchestrator.execute(a.getId(),
                            recentData != null ? recentData : Map.of(),
                            "scheduler");
                });
    }

    private Map<String, Object> buildDbFallbackPayload(String deviceId, String automationName) {
        try {
            var record = mainService.getLastFullData(deviceId);
            if (record == null) {
                log.warn("🐕 [{}] DB fallback: no record for '{}' — last_seen=0",
                        automationName, deviceId);
                return new HashMap<>(Map.of("last_seen", 0L));
            }
            Map<String, Object> payload = new HashMap<>();
            if (record.getData() != null) payload.putAll(record.getData());
            long lastSeenMs = record.getUpdateDate() != null
                    ? record.getUpdateDate().getEpochSecond() * 1000L : 0L;
            payload.put("last_seen", lastSeenMs);
            log.info("🐕 [{}] DB fallback: device='{}' last record={}",
                    automationName, deviceId, record.getUpdateDate());
            return payload;
        } catch (Exception e) {
            log.error("❌ [{}] DB fallback failed for '{}': {}",
                    automationName, deviceId, e.getMessage());
            return new HashMap<>(Map.of("last_seen", 0L));
        }
    }

    /**
     * Returns true if the compiled plan contains any condition with
     * conditionType="stale". Checks the condition tree only (branches removed).
     */
    private boolean planHasStaleCondition(String automationId) {
        ExecutionPlan plan = planCache.get(automationId);
        if (plan == null) return false;
        if (plan.getConditionTree() == null) return false;
        return plan.getConditionTree().stream()
                .anyMatch(n -> n.getCondition() != null
                        && "stale".equals(n.getCondition().getConditionType()));
    }

    @Scheduled(fixedRate = 65_000)
    public void pollWledState() {
        mainService.getAllDevice().stream()
                .filter(d -> "WLED".equals(d.getType()))
                .forEach(d -> new Wled(mqttOutboundChannel, d).publishForInfo(d.getId()));
    }

    public AutomationGraphValidator.ValidationResult validateBeforeSave(
            AutomationDetail detail, String homeId) {
        var automation = buildAutomation(detail, homeId);

        return graphValidator.validate(automation);
    }

    private Automation buildAutomation(AutomationDetail detail, String homeId) {
        var automationBuilder = Automation.builder()
                .isEnabled(true).updateDate(new Date()).isActive(false);
        if (detail.getId() != null && !detail.getId().isEmpty())
            automationBuilder.id(detail.getId());
        detail.setHomeId(homeId);
        detail.getNodes().stream()
                .filter(n -> n.getData().getTriggerData() != null)
                .findFirst().ifPresent(tn -> {
                    var t = tn.getData().getTriggerData();
                    automationBuilder.trigger(new Automation.Trigger(
                            t.getDeviceId(), t.getType(), t.getValue(), t.getKey(),
                            t.getKeys().stream().map(k -> k.getKey()).toList(),
                            t.getName(), t.getPriority(), t.getNodeId(), t.getSources(),
                            t.getCoalitionMode(), t.getCoalitionWindowSeconds()
                    ));
                    automationBuilder.name(t.getName());
                });

        // ── Actions ───────────────────────────────────────────────────────
        automationBuilder.actions(
                detail.getNodes().stream()
                        .filter(n -> n.getData().getActionData() != null)
                        .map(n -> {
                            var a = n.getData().getActionData();
                            return new Automation.Action(
                                    a.getKey(), a.getDeviceId(), a.getData(), a.getName(),
                                    a.getIsEnabled(), a.getRevert(), a.getConditionGroup(),
                                    a.getOrder(), a.getDelaySeconds(),
                                    a.getPreviousNodeRef(), a.getNodeId());
                        }).toList());

        // ── Conditions ────────────────────────────────────────────────────
        // positiveChildren, negativeChildren, and fanoutMode are read from
        // the frontend node data if present. If absent, the compiler derives
        // them from previousNodeRef edges — so the frontend does NOT need to
        // send these fields explicitly.
        automationBuilder.conditions(
                detail.getNodes().stream()
                        .map(n -> n.getData().getConditionData())
                        .filter(Objects::nonNull)
                        .map(c -> {
                            // Carry through optional explicit children if the frontend
                            // supplies them (future: UI toggle for FIRST_MATCH fan-out mode).
                            // If null the compiler resolves from edges — no action needed.
                            return new Automation.Condition(
                                    c.getNodeId() != null ? c.getNodeId() : "",
                                    c.getCondition(), c.getValueType(), c.getAbove(), c.getBelow(),
                                    c.getValue(), c.getTime(), c.getTriggerKey(), c.getIsExact(),
                                    c.getScheduleType(), c.getFromTime(), c.getToTime(), c.getDays(),
                                    c.getSolarType(), c.getOffsetMinutes(), c.getIntervalMinutes(),
                                    c.getDurationMinutes(), c.isEnabled(), c.getPreviousNodeRef(),
                                    c.getDeviceId(), c.getMemoryPolicy(), c.getMemoryPolicyValue(),
                                    c.getFanoutMode()
                            );
                        })
                        .collect(Collectors.toList()));

        // ── Operators removed ─────────────────────────────────────────────
        // Operator nodes are no longer part of the execution model.
        // They are stored in the AutomationDetail for UI rendering only
        // and are NOT saved to the Automation model or read by the compiler.
        automationBuilder.operators(List.of());

        automationBuilder.homeId(detail.getHomeId());
        return automationBuilder.build();
    }
    // ═════════════════════════════════════════════════════════════════════
    // SAVE AUTOMATION
    // ═════════════════════════════════════════════════════════════════════

    public String saveAutomationDetailInternal(AutomationDetail detail, String user, String homeId) {
        log.info("Saving automation: {}", detail.getId());

        var automation = buildAutomation(detail, homeId);

        List<String> subscribers = automation.getTrigger().getSources().stream()
                .filter(s -> "primary".equals(s.getRole()))
                .map(TriggerSource::getDeviceId)
                .toList();
        automation.setSubscriberDeviceIds(subscribers);
        automation.setTriggerDeviceType(
                mainService.getDevice(automation.getTrigger().getDeviceId()).getType());

        Automation saved = automationRepository.save(automation);
        detail.setId(saved.getId());
        detail.setUpdateDate(new Date());
        automationDetailRepository.save(detail);

        // ── Compile ExecutionPlan ─────────────────────────────────────────
        ExecutionPlan plan = null;
        try {
            plan = planCompiler.compile(saved);
            orchestrator.updatePlan(saved.getId(), plan);
            planRepository.save(plan);
            log.info("✅ ExecutionPlan compiled for '{}'", saved.getName());
        } catch (Exception e) {
            log.error("❌ Plan compilation failed for '{}': {}", saved.getName(), e.getMessage(), e);
            notificationService.sendNotification(
                    "Plan compilation failed for " + saved.getName(), "error", homeId);
        }

        // ── Reset runtime state ───────────────────────────────────────────
        stateStore.forceWrite(saved.getId(), AutomationRuntimeState.idle());

        // ── Clean up schedule keys ────────────────────────────────────────
        // Use the compiled plan's condition tree for accurate node IDs.
        // Fall back to automation.getConditions() if compilation failed.
        if (plan != null && plan.getConditionTree() != null) {
            plan.getConditionTree().forEach(n ->
                    stateStore.deleteIntervalAndRunningKeys(saved.getId(), n.getNodeId()));
        } else if (saved.getConditions() != null) {
            saved.getConditions().forEach(c ->
                    stateStore.deleteIntervalAndRunningKeys(saved.getId(), c.getNodeId()));
        }

        automationVersionService.snapshot(saved, detail, "system", null);
        notificationService.sendNotification("Automation saved successfully", "success", homeId);
        return "success";
    }

    /**
     * Pre-warms Redis with the latest DB data for every non-primary coalition
     * source device that currently has no Redis entry.
     * <p>
     * Called synchronously before orchestrator.execute() — this is intentional.
     * The pre-warm must complete before evaluation starts so the evaluator finds
     * data in Redis. The DB calls here are bounded by the number of secondary
     * devices (typically 1–3) and are far cheaper than doing them mid-evaluation
     * on the async automationExecutor thread pool.
     * <p>
     * Only devices with NO current Redis data are fetched — devices that are
     * actively publishing will already have fresh Redis entries and are skipped.
     * <p>
     * The fetched data is written to Redis with a short TTL (30 seconds) to
     * prevent stale pre-warmed data from being treated as live data.
     * The evaluator's normal Redis read will then find this data within TTL.
     */
    private void prewarmSecondaryDevices(Automation automation) {
        if (automation.getTrigger().getSources() == null) return;

        automation.getTrigger().getSources().stream()
                .filter(s -> !"primary".equals(s.getRole()))
                .filter(s -> s.getDeviceId() != null && !s.getDeviceId().isBlank())
                .forEach(s -> {
                    String deviceId = s.getDeviceId();
                    Map<String, Object> existing = redisService.getRecentDeviceData(deviceId);
                    if (existing != null && !existing.isEmpty()) return; // already warm

                    try {
                        var record = mainService.getLastFullData(deviceId);
                        if (record == null) {
                            log.warn("⚠️ [{}] Pre-warm: no DB record for secondary device '{}'",
                                    automation.getName(), deviceId);
                            return;
                        }
                        Map<String, Object> payload = new HashMap<>();
                        if (record.getData() != null) payload.putAll(record.getData());
                        if (record.getUpdateDate() != null)
                            payload.put("last_seen", record.getUpdateDate().getEpochSecond() * 1000L);

                        // Write to Redis with 30s TTL — long enough for the evaluator to read it,
                        // short enough that truly dead devices don't look alive indefinitely.
                        redisService.setRecentDeviceDataWithTtl(deviceId, payload, 30);

                        log.debug("🔥 [{}] Pre-warmed secondary device '{}' (last DB record: {})",
                                automation.getName(), deviceId, record.getUpdateDate());

                    } catch (Exception e) {
                        log.warn("⚠️ [{}] Pre-warm failed for secondary device '{}': {}",
                                automation.getName(), deviceId, e.getMessage());
                    }
                });
    }
    // ═════════════════════════════════════════════════════════════════════
    // DELETE AUTOMATION
    // ═════════════════════════════════════════════════════════════════════

    public Map<String, String> deleteAutomation(String id, String user, String homeId) {
        Automation automation = automationRepository.findByIdAndHomeId(id, homeId).orElse(null);
        if (automation == null)
            return Map.of("status", "error", "reason", "Automation not found");

        try {
            automation.setIsEnabled(false);
            automationRepository.save(automation);

            planCache.delete(id);
            stateStore.deleteAllKeys(id);
            scheduledAutomationManager.cancel(id);
            automationRepository.deleteById(id);
            automationDetailRepository.deleteById(id);
            orchestrator.invalidatePlan(id);

            log.info("🗑️ Automation '{}' (id={}) deleted by '{}'",
                    automation.getName(), id, user);
            notificationService.sendNotification(
                    "'" + automation.getName() + "' deleted", "success", homeId);
            return Map.of("status", "success", "deletedId", id);

        } catch (Exception e) {
            log.error("Delete failed for automation '{}': {}", id, e.getMessage(), e);
            notificationService.sendNotification("Delete failed: " + e.getMessage(), "error", homeId);
            return Map.of("status", "error", "reason", e.getMessage());
        }
    }


    // ═════════════════════════════════════════════════════════════════════
    // COPY / ROLLBACK
    // ═════════════════════════════════════════════════════════════════════

    public Map<String, Object> copyAutomation(String id, String user, String homeId) {
        Automation original = automationRepository.findByIdAndHomeId(id, homeId).orElse(null);
        if (original == null) return new HashMap<>();

        Automation copy = Automation.builder()
                .name(original.getName() + " (copy)")
                .trigger(original.getTrigger())
                .homeId(homeId)
                .triggerDeviceType(original.getTriggerDeviceType())
                .conditions(original.getConditions())
                .actions(original.getActions())
                .operators(List.of())   // operators no longer used
                .isEnabled(false).isActive(false).updateDate(new Date())
                .build();
        Automation saved = automationRepository.save(copy);

        automationDetailRepository.findById(id).ifPresent(d -> {
            AutomationDetail dc = new AutomationDetail();
            dc.setId(saved.getId());
            dc.setNodes(d.getNodes());
            dc.setEdges(d.getEdges());
            dc.setViewport(d.getViewport());
            dc.setUpdateDate(new Date());
            automationDetailRepository.save(dc);
        });

        try {
            ExecutionPlan plan = planCompiler.compile(saved);
            orchestrator.updatePlan(saved.getId(), plan);
        } catch (Exception e) {
            log.warn("Plan compilation failed for copy '{}': {}", saved.getName(), e.getMessage());
        }

        stateStore.forceWrite(saved.getId(), AutomationRuntimeState.idle());
        log.info("📋 '{}' copied to '{}' by {}", original.getName(), saved.getName(), user);
        notificationService.sendNotification("'" + original.getName() + "' copied", "success", homeId);
        return Map.of("status", "success", "newId", saved.getId(), "newName", saved.getName());
    }

    public String rollbackToVersion(String automationId, int targetVersion, String user, String homeId) {
        try {
            AutomationVersionService.RollbackResult rollback =
                    automationVersionService.rollback(automationId, targetVersion);
            AutomationDetail detail = rollback.detail();
            detail.setUpdateDate(new Date());
            String result = saveAutomationDetailInternal(detail, user, homeId);
            if ("success".equals(result)) {
                automationRepository.findById(automationId).ifPresent(a ->
                        automationVersionService.snapshot(a, detail, user,
                                "Rolled back to version " + targetVersion));
                notificationService.sendNotification(
                        "Rolled back to version " + targetVersion, "success", homeId);
            }
            return result;
        } catch (Exception e) {
            log.error("Rollback failed for '{}': {}", automationId, e.getMessage(), e);
            notificationService.sendNotification("Rollback failed: " + e.getMessage(), "error", homeId);
            return "error: " + e.getMessage();
        }
    }


    // ═════════════════════════════════════════════════════════════════════
    // SCENE EXECUTION
    // ═════════════════════════════════════════════════════════════════════

    public void executeSceneActions(Automation automation,
                                    List<Automation.Action> actions,
                                    Map<String, Object> payload,
                                    String user) {
        List<ExecutionPlan.CompiledAction> compiled = actions.stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsEnabled()))
                .map(a -> {
                    String dt;
                    try {
                        dt = mainService.getDevice(a.getDeviceId()).getType();
                    } catch (Exception e) {
                        dt = "sensor";
                    }
                    return ExecutionPlan.CompiledAction.builder()
                            .nodeId(a.getNodeId()).deviceId(a.getDeviceId())
                            .key(a.getKey()).data(a.getData()).order(a.getOrder())
                            .delaySeconds(a.getDelaySeconds()).name(a.getName())
                            .deviceType(dt).build();
                })
                .sorted(Comparator.comparingInt(
                        c -> c.getOrder() != 0 ? c.getOrder() : Integer.MAX_VALUE))
                .toList();
        String traceId = "evt-" + automation.getId() + "-" + System.currentTimeMillis();
        dispatcher.dispatch(compiled, payload, user, automation.getId(),
                automation.getName(), traceId, automation.getHomeId());
    }


    // ═════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ═════════════════════════════════════════════════════════════════════

    private void writeDeviceData(String deviceId, Map<String, Object> payload) {
        Map<String, Object> enriched = new HashMap<>(payload);
        enriched.put("last_seen", System.currentTimeMillis());
        redisService.setRecentDeviceData(deviceId.toLowerCase(Locale.ROOT), enriched);
    }

    private boolean planHasDataDrivenTrigger(String automationId) {
        ExecutionPlan plan = planCache.get(automationId);
        return plan != null && plan.hasDataDrivenTrigger();
    }

    private void warnMissingSecondaryData(Automation a) {
        if (a.getTrigger().getSources() == null) return;
        a.getTrigger().getSources().stream()
                .filter(s -> !"primary".equals(s.getRole()))
                .forEach(s -> {
                    Map<String, Object> d = redisService.getRecentDeviceData(s.getDeviceId());
                    if (d == null || d.isEmpty())
                        log.warn("⚠️ [{}] Secondary device '{}' has no cached Redis data",
                                a.getName(), s.getDeviceId());
                });
    }

    private void sendToTopic(String topic, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            mqttOutboundChannel.send(MessageBuilder.withPayload(json)
                    .setHeader("mqtt_topic", topic).build());
        } catch (Exception e) {
            log.error("MQTT send error: {}", e.getMessage());
        }
    }

    private String rebootDevice(Device device) {
        if (device == null) return "Device not found";
        Map<String, Object> map = Map.of(
                "deviceId", device.getId(), "reboot", true, "key", "reboot");
        messagingTemplate.convertAndSend("/topic/action." + device.getId(), Optional.of(map));
        sendToTopic(TOPIC_ACTION + device.getId(), map);
        try {
            new RestTemplate().getForObject(device.getAccessUrl() + "/restart", String.class);
        } catch (Exception e) {
            notificationService.sendNotification("Reboot failed: " + device.getName(), "error", device.getHomeId());
        }
        return "Rebooting device";
    }

    private String handleWLED(String deviceId, Map<String, Object> payload, String user) {
        return deviceRepository.findById(deviceId)
                .map(device -> {
                    try {
                        return new Wled(mqttOutboundChannel, device).handleAction(payload);
                    } catch (Exception e) {
                        log.error("WLED error: {}", e.getMessage());
                        return "Error";
                    }
                })
                .orElse("Not found");
    }
}