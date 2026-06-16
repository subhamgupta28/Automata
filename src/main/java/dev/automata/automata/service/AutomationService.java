package dev.automata.automata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.automata.automata.automation.AutomationAbTestService;
import dev.automata.automata.automation.AutomationValidationService;
import dev.automata.automata.automation.AutomationVersionService;
import dev.automata.automata.automation.ScheduledAutomationManager;
import dev.automata.automata.dto.AutomationRuntimeState;
import dev.automata.automata.dto.LiveEvent;
import dev.automata.automata.model.Automation;
import dev.automata.automata.model.AutomationDetail;
import dev.automata.automata.model.Device;
import dev.automata.automata.model.TriggerSource;
import dev.automata.automata.modules.Wled;
import dev.automata.automata.repository.AutomationDetailRepository;
import dev.automata.automata.repository.AutomationRepository;
import dev.automata.automata.repository.DeviceRepository;
import dev.automata.automata.repository.ExecutionPlanRepository;
import dev.automata.automata.v2.*;
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
 * AutomationService — public API surface only.
 * <p>
 * Changes vs previous version
 * ───────────────────────────
 * 1. handleAction() — coalition-aware routing (Point 1)
 * Uses automationRepository.findByAnyTriggerDeviceId(deviceId) to find
 * automations where deviceId is ANY coalition member (not just primary trigger).
 * Falls back to findByTrigger_DeviceId for automations without a coalition.
 * Passes firingDeviceId to orchestrator.execute() overload.
 * <p>
 * 2. triggerPeriodicAutomations() — corrected filter (Bug 3 fix)
 * Was: !hasOnlyScheduledConditions (excluded scheduled-only → wrong for mixed)
 * Now: hasAnyScheduledConditions (includes any automation with ≥1 scheduled condition)
 * <p>
 * 3. No other logic changes — all execution flow stays in the orchestrator.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationService {

    private final AutomationRepository automationRepository;
    private final AutomationDetailRepository automationDetailRepository;
    private final AutomationVersionService automationVersionService;
    private final AutomationValidationService validationService;
    private final AutomationAbTestService abTestService;
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

    public List<Automation> getActions() {
        return automationRepository.findAll();
    }

    public AutomationDetail getAutomationDetail(String id) {
        return automationDetailRepository.findById(id).orElse(null);
    }

    public String disableAutomation(String id, Boolean enabled) {
        automationRepository.findById(id).ifPresent(a -> {
            a.setIsEnabled(enabled);
            automationRepository.save(a);
            notificationService.sendNotification("Automation updated", "success");
            orchestrator.invalidatePlan(id);
        });
        return "success";
    }


    // ═════════════════════════════════════════════════════════════════════
    // HANDLE ACTION  (live device event entry point)
    // ═════════════════════════════════════════════════════════════════════

    public String handleAction(String deviceId, Map<String, Object> payload,
                               String deviceType, String user) {

        if ("WLED".equals(deviceType)) {
            return handleWLED(deviceId, payload, user);
        }

        if ("System".equals(deviceType)) {
            String key = payload.get("key").toString();
            String data = payload.get(key).toString();
            if ("alert".equals(key)) notificationService.sendNotification("", data);
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
            deviceRepository.findById(id)
                    .ifPresent(d -> handleAction(id, req, d.getType(), user));
            return "success";
        }

        if (payload.containsKey("direct")
                && Boolean.parseBoolean(payload.get("direct").toString())) {
            dispatcher.dispatchDirect(deviceId, payload);
            return "Direct action sent";
        }

        // Write live payload to Redis — overwrites previous value, no MongoDB
        writeDeviceData(deviceId, payload);

        // ── Point 1: coalition-aware automation lookup ──────────────────────
        // Find automations where this deviceId is ANY coalition member OR the legacy trigger.
        // For non-coalition automations this degrades to findByTrigger_DeviceId behaviour.
        List<Automation> automations = findAutomationsForDevice(deviceId);
        if (automations.isEmpty()) return "No automations for device";

        automations.stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsEnabled()))
                .forEach(a -> {
                    ExecutionPlan plan = planCache.get(a.getId());
                    if (plan != null && plan.hasCoalition()) {
                        // Pass the firing deviceId so CoalitionGuard can record it
                        orchestrator.execute(a.getId(), payload, user, deviceId);
                    } else {
                        // Legacy single-trigger path
                        orchestrator.execute(a.getId(), payload, user);
                    }
                });

        return "Action routed to " + automations.size() + " automation(s)";
    }

    /**
     * Point 1: finds automations for which the given deviceId is a relevant trigger.
     * <p>
     * For coalition automations: deviceId may be any member (primary, secondary, veto).
     * For legacy automations: deviceId must match trigger.deviceId.
     * <p>
     * Implementation note: the simplest approach is to check both sources:
     * 1. Legacy: findByTrigger_DeviceId (existing index, fast)
     * 2. Coalition: findByCoalitionMemberDeviceId (new index — see AutomationRepository)
     * Then deduplicate by ID.
     * <p>
     * If you haven't added the coalition repository method yet, this falls back to
     * the legacy query and coalition devices that are not the primary trigger won't
     * fire — which is safe (existing behaviour preserved).
     */
    private List<Automation> findAutomationsForDevice(String deviceId) {
        Set<String> seen = new LinkedHashSet<>();
        List<Automation> result = new ArrayList<>();

        // Legacy primary-trigger lookup (always present, indexed)
        automationRepository.findByTrigger_DeviceId(deviceId).stream()
                .filter(a -> seen.add(a.getId()))
                .forEach(result::add);

        // Coalition member lookup (new — only present if repository method added)
        try {
            automationRepository.findByCoalitionMemberDeviceId(deviceId).stream()
                    .filter(a -> seen.add(a.getId()))
                    .forEach(result::add);
        } catch (Exception e) {
            // Method not yet implemented in repository — safe to ignore
            log.trace("Coalition lookup not available for device '{}': {}", deviceId, e.getMessage());
        }

        return result;
    }

    public String ackAction(String deviceId, Map<String, Object> payload) {
        if (payload.containsKey("actionAck")) {
            if (payload.containsKey("actionType"))
                notificationService.sendNotification("Action sent to device", "success");
            if (payload.containsKey("_cid"))
                deliveryTracker.confirm(payload.get("_cid").toString());
        }
        return "success";
    }

    public String rebootAllDevices() {
        notificationService.sendNotification("Rebooting All Devices", "success");
        deviceRepository.findAll().forEach(this::rebootDevice);
        notificationService.sendNotification("Reboot Complete", "success");
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
        messagingTemplate.convertAndSend("/topic/action." + deviceId, payload);
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

    /**
     * Periodic evaluation tick — every 12 seconds.
     * <p>
     * Bug 3 fix: filter changed from !hasOnlyScheduledConditions (inverted logic)
     * to hasAnyScheduledConditions (correct: include automations that NEED periodic
     * ticking because they have at least one scheduled gate/condition).
     * <p>
     * "Periodic Bat 500 charging": has scheduled gate → included ✓
     * "Emergency Bat 500 Charging": no scheduled conditions → excluded ✓ (event-driven only)
     * "Charging stop at 100": no scheduled conditions → excluded ✓
     * "TESTING": has scheduled gates → included ✓
     * "Light On": has scheduled gates → included ✓
     * <p>
     * Point 1: periodic ticks use the legacy single-trigger path (firingDeviceId = primary).
     * Coalition tracking is only meaningful for live events from individual devices.
     */
    @Scheduled(fixedRate = 12_000)
    public void triggerPeriodicAutomations() {
        if (!featureService.isFeatureEnabled("PERIODIC_AUTOMATION_SERVICE")) return;

        automationRepository.findEnabledForExecution().stream()
                // Fix Bug 3: was !hasOnlyScheduledConditions (inverted / wrong semantics)
                .filter(scheduledAutomationManager::hasAnyScheduledConditions)
                .forEach(a -> {
                    String deviceId = a.getTrigger().getDeviceId();
                    Map<String, Object> recentData = redisService.getRecentDeviceData(deviceId);
                    boolean hasRedisData = recentData != null && !recentData.isEmpty();

                    if (!hasRedisData && planHasDataDrivenTrigger(a.getId())) {

                        // Stale-condition automations MUST run when Redis is empty —
                        // missing data is exactly what they detect.
                        // Build a synthetic payload from the DB record's updateDate
                        // so evalSingleCondition has a last_seen to compare against.
                        if (planHasStaleCondition(a.getId())) {
                            Map<String, Object> dbPayload = buildDbFallbackPayload(
                                    deviceId, a.getName());
                            log.info("🐕 [{}] Redis empty — running stale check with DB payload",
                                    a.getName());
                            warnMissingSecondaryData(a);
                            orchestrator.execute(a.getId(), dbPayload, "scheduler");
                            return;
                        }

                        log.warn("⚠️ [{}] Skipping — no cached data for device '{}'",
                                a.getName(), deviceId);
                        return;
                    }

                    warnMissingSecondaryData(a);
                    orchestrator.execute(a.getId(),
                            recentData != null ? recentData : Map.of(),
                            "scheduler");
                });
    }

    /**
     * Builds a minimal payload for a device that has no Redis data.
     * Fetches the device's last record from MongoDB and injects last_seen
     * as epoch-ms so the stale evaluator can compute (now - last_seen).
     * <p>
     * If the DB record has a getData() map, it is included in full so any
     * other conditions on the same automation can also evaluate normally.
     * If getData() is null or empty, only last_seen is injected.
     * If no DB record exists at all, last_seen=0 is injected — the evaluator
     * treats 0 as "never seen" and the stale condition returns true immediately.
     */
    private Map<String, Object> buildDbFallbackPayload(String deviceId, String automationName) {
        try {
            var record = mainService.getLastFullData(deviceId);
            if (record == null) {
                log.warn("🐕 [{}] DB fallback: no record found for device '{}' — last_seen=0",
                        automationName, deviceId);
                return new HashMap<>(Map.of("last_seen", 0L));
            }

            Map<String, Object> payload = new HashMap<>();
            if (record.getData() != null) payload.putAll(record.getData());

            long lastSeenMs = record.getUpdateDate() != null
                    ? record.getUpdateDate().getEpochSecond() * 1000L
                    : 0L;
            payload.put("last_seen", lastSeenMs);

            log.info("🐕 [{}] DB fallback: device='{}' last DB record={}",
                    automationName, deviceId, record.getUpdateDate());
            return payload;

        } catch (Exception e) {
            log.error("❌ [{}] DB fallback failed for device '{}': {}",
                    automationName, deviceId, e.getMessage());
            return new HashMap<>(Map.of("last_seen", 0L));
        }
    }

    /**
     * Returns true if the compiled plan contains any condition with
     * conditionType="stale".  These automations must never be skipped
     * when Redis is empty — no data IS the alert condition.
     */
    private boolean planHasStaleCondition(String automationId) {
        ExecutionPlan plan = planCache.get(automationId);
        if (plan == null) return false;

        // Check condition tree nodes
        if (plan.getConditionTree() != null) {
            boolean inTree = plan.getConditionTree().stream()
                    .anyMatch(n -> n.getCondition() != null
                            && "stale".equals(n.getCondition().getConditionType()));
            if (inTree) return true;
        }

        // Also check gate branches — stale can be a gate condition too
        if (plan.getBranches() != null) {
            return plan.getBranches().stream()
                    .anyMatch(b -> b.getGateCondition() != null
                            && "stale".equals(b.getGateCondition().getConditionType()));
        }

        return false;
    }

    @Scheduled(fixedRate = 65_000)
    public void pollWledState() {
        mainService.getAllDevice().stream()
                .filter(d -> "WLED".equals(d.getType()))
                .forEach(d -> new Wled(mqttOutboundChannel, d).publishForInfo(d.getId()));
    }


    // ═════════════════════════════════════════════════════════════════════
    // SAVE AUTOMATION
    // ═════════════════════════════════════════════════════════════════════

    public String saveAutomationDetailWithValidation(AutomationDetail detail) {
        List<String> errors = validationService.validate(detail);
        if (!errors.isEmpty()) {
            notificationService.sendNotification(
                    "Validation failed: " + String.join(", ", errors), "error");
            return "validation_failed: " + String.join("; ", errors);
        }
        return saveAutomationDetailInternal(detail);
    }

    public String saveAutomationDetail(AutomationDetail detail) {
        return saveAutomationDetailInternal(detail);
    }

    public String saveAutomationDetailAnnotated(AutomationDetail detail,
                                                String savedBy, String changeNote) {
        String result = saveAutomationDetailInternal(detail);
        if ("success".equals(result))
            automationRepository.findById(detail.getId()).ifPresent(a ->
                    automationVersionService.snapshot(a, detail, savedBy, changeNote));
        return result;
    }

    private String saveAutomationDetailInternal(AutomationDetail detail) {
        log.info("Saving automation: {}", detail.getId());

        var automationBuilder = Automation.builder()
                .isEnabled(true).updateDate(new Date()).isActive(false);

        if (detail.getId() != null && !detail.getId().isEmpty())
            automationBuilder.id(detail.getId());

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

        automationBuilder.conditions(
                detail.getNodes().stream()
                        .map(n -> n.getData().getConditionData())
                        .filter(Objects::nonNull)
                        .map(c -> new Automation.Condition(
                                c.getNodeId() != null ? c.getNodeId() : "",
                                c.getCondition(), c.getValueType(), c.getAbove(), c.getBelow(),
                                c.getValue(), c.getTime(), c.getTriggerKey(), c.getIsExact(),
                                c.getScheduleType(), c.getFromTime(), c.getToTime(), c.getDays(),
                                c.getSolarType(), c.getOffsetMinutes(), c.getIntervalMinutes(),
                                c.getDurationMinutes(), c.isEnabled(), c.getPreviousNodeRef(),
                                c.getDeviceId(), c.getMemoryPolicy(), c.getMemoryPolicyValue()
                        ))
                        .collect(Collectors.toList()));

        automationBuilder.operators(
                detail.getNodes().stream()
                        .map(n -> n.getData().getOperators())
                        .filter(Objects::nonNull)
                        .map(c -> new Automation.Operator(
                                c.getType(), c.getLogicType(),
                                c.getPreviousNodeRef(), c.getNodeId(), c.getPriority()))
                        .collect(Collectors.toList()));

        automationBuilder.homeId(detail.getHomeId());
        Automation automation = automationBuilder.build();
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

        // ── Compile ExecutionPlan ──────────────────────────────────────────
        try {
            ExecutionPlan plan = planCompiler.compile(saved);
            orchestrator.updatePlan(saved.getId(), plan);
            planRepository.save(plan);
            log.info("✅ ExecutionPlan compiled for '{}'", saved.getName());
        } catch (Exception e) {
            log.error("❌ Plan compilation failed for '{}': {}", saved.getName(), e.getMessage(), e);
            notificationService.sendNotification(
                    "Plan compilation failed for " + saved.getName(), "error");
        }

        // ── Reset runtime state (clears condition memories and coalition state too) ──
        stateStore.forceWrite(saved.getId(), AutomationRuntimeState.idle());

        // ── Clean up schedule keys ─────────────────────────────────────────
        if (saved.getConditions() != null)
            saved.getConditions().forEach(c ->
                    stateStore.deleteIntervalAndRunningKeys(saved.getId(), c.getNodeId()));

        automationVersionService.snapshot(saved, detail, "system", null);
        notificationService.sendNotification("Automation saved successfully", "success");
        return "success";
    }


    // ═════════════════════════════════════════════════════════════════════
    // DELETE AUTOMATION
    // ═════════════════════════════════════════════════════════════════════

    public Map<String, String> deleteAutomation(String id, String user) {
        Automation automation = automationRepository.findById(id).orElse(null);
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
                    "'" + automation.getName() + "' deleted", "success");
            return Map.of("status", "success", "deletedId", id);

        } catch (Exception e) {
            log.error("Delete failed for automation '{}': {}", id, e.getMessage(), e);
            notificationService.sendNotification("Delete failed: " + e.getMessage(), "error");
            return Map.of("status", "error", "reason", e.getMessage());
        }
    }


    // ═════════════════════════════════════════════════════════════════════
    // COPY / ROLLBACK
    // ═════════════════════════════════════════════════════════════════════

    public Map<String, Object> copyAutomation(String id, String user) {
        Automation original = automationRepository.findById(id).orElse(null);
        if (original == null) return new HashMap<>();

        Automation copy = Automation.builder()
                .name(original.getName() + " (copy)")
                .trigger(original.getTrigger())
                .triggerDeviceType(original.getTriggerDeviceType())
                .conditions(original.getConditions())
                .actions(original.getActions())
                .operators(original.getOperators())
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
        notificationService.sendNotification("'" + original.getName() + "' copied", "success");
        return Map.of("status", "success", "newId", saved.getId(), "newName", saved.getName());
    }

    public String rollbackToVersion(String automationId, int targetVersion, String user) {
        try {
            AutomationVersionService.RollbackResult rollback =
                    automationVersionService.rollback(automationId, targetVersion);
            AutomationDetail detail = rollback.detail();
            detail.setUpdateDate(new Date());
            String result = saveAutomationDetailInternal(detail);
            if ("success".equals(result)) {
                automationRepository.findById(automationId).ifPresent(a ->
                        automationVersionService.snapshot(a, detail, user,
                                "Rolled back to version " + targetVersion));
                notificationService.sendNotification(
                        "Rolled back to version " + targetVersion, "success");
            }
            return result;
        } catch (Exception e) {
            log.error("Rollback failed for '{}': {}", automationId, e.getMessage(), e);
            notificationService.sendNotification("Rollback failed: " + e.getMessage(), "error");
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
        dispatcher.dispatch(compiled, payload, user, automation.getId(), automation.getName(), traceId);
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
        messagingTemplate.convertAndSend("/topic/action." + device.getId(), map);
        sendToTopic(TOPIC_ACTION + device.getId(), map);
        try {
            new RestTemplate().getForObject(device.getAccessUrl() + "/restart", String.class);
        } catch (Exception e) {
            notificationService.sendNotification("Reboot failed: " + device.getName(), "error");
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