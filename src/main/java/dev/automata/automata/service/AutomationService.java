package dev.automata.automata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.automata.automata.automation.*;
import dev.automata.automata.dto.*;
import dev.automata.automata.model.*;
import dev.automata.automata.modules.Wled;
import dev.automata.automata.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationService {

    private final DeviceRepository deviceRepository;
    private final AutomationRepository automationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisService redisService;
    private final MainService mainService;
    private final NotificationService notificationService;
    private final AutomationDetailRepository automationDetailRepository;
    private final DeviceActionStateRepository deviceActionStateRepository;
    private final MessageChannel mqttOutboundChannel;
    private final AutomationLogRepository automationLogRepository;
    private final AutomationValidationService validationService;
    private final FeatureService featureService;
    private final ApplicationEventPublisher eventPublisher;
    private final AutomationLogBuffer logBuffer;
    private final ActionDeliveryTracker deliveryTracker;
    private final ScheduledAutomationManager scheduledAutomationManager;
    private final AutomationEngine automationEngine;
    private final AutomationVersionService automationVersionService;
    // FIX Bug#1: inject AutomationAbTestService so shadowEvaluate() can be called
    private final AutomationAbTestService abTestService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String TOPIC_ACTION = "action/";
    private static final long AUTOMATION_TIMEOUT_SECONDS = 30;
    private static final long LOCK_TTL_SECONDS = 60;

    private final Executor automationExecutor = new ThreadPoolExecutor(
            2, 4, 30L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(50),
            new ThreadFactory() {
                private final AtomicInteger count = new AtomicInteger();

                public Thread newThread(Runnable r) {
                    return new Thread(r, "automation-" + count.incrementAndGet());
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    private static final ScheduledExecutorService delayScheduler =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "automation-delay-scheduler");
                t.setDaemon(true);
                return t;
            });


    // ═════════════════════════════════════════════════════════════════════
    // PUBLIC API
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
            refreshCacheForAutomation(a);
        });
        return "success";
    }

    public String ackAction(String deviceId, Map<String, Object> payload) {
        if (payload.containsKey("actionAck")) {
            if (payload.containsKey("actionType")) {
                notificationService.sendNotification("Action sent to device", "success");
            }
            if (payload.containsKey("_cid")) {
                deliveryTracker.confirm(payload.get("_cid").toString());
            }
        }
        return "success";
    }

    public String handleAction(String deviceId, Map<String, Object> payload,
                               String deviceType, String user) {
        if ("WLED".equals(deviceType)) {
            handleWLED(deviceId, payload, user);
            return "success";
        }

        if ("System".equals(deviceType)) {
            var key = payload.get("key").toString();
            var data = payload.get(key).toString();
            if ("alert".equals(payload.get("key")))
                notificationService.sendNotification("", data);
            if ("app_notify".equals(payload.get("key")))
                notificationService.sendNotify("Automation", data, "low");
        }

        if ("reboot".equals(payload.get("key"))) {
            return rebootDevice(mainService.getDevice(deviceId));
        }

        if (payload.containsKey("automation")) {
            var id = payload.get(payload.get("key").toString()).toString();
            automationRepository.findById(id).ifPresent(
                    a -> executeAutomationImmediate(a, new HashMap<>(), user));
            return "success";
        }

        if (payload.containsValue("master")) {
            var id = payload.get("deviceId").toString();
            var device = deviceRepository.findById(id);
            var key = payload.get("key").toString();
            var value = Integer.parseInt(payload.get("value").toString());
            var req = new HashMap<String, Object>();
            req.put("key", key);
            req.put(key, value);
            req.put("direct", true);
            req.put("deviceId", id);
            device.ifPresent(d -> handleAction(id, req, d.getType(), user));
        }

        if (payload.containsKey("direct") && Boolean.parseBoolean(payload.get("direct").toString())) {
            sendDirectAction(deviceId, payload);
            return "No saved action found but sent directly";
        }

        automationRepository.findByTrigger_DeviceId(deviceId)
                .forEach(a -> eventPublisher.publishEvent(new PeriodicCheckEvent(this, a, payload, "user")));

        return "Action successfully sent!";
    }

    public String rebootAllDevices() {
        notificationService.sendNotification("Rebooting All Devices", "success");
        deviceRepository.findAll().forEach(this::rebootDevice);
        notificationService.sendNotification("Reboot Complete", "success");
        return "success";
    }

    public Object sendConditionToDevice(String deviceId) {
        var payload = new HashMap<String, Object>();
        var keyJoiner = new StringJoiner(",");
        for (Automation a : automationRepository.findByTrigger_DeviceId(deviceId)) {
            var conditions = a.getConditions();
            if (conditions == null || conditions.isEmpty()) continue;
            var c = conditions.get(0);
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

    @Async
    @EventListener
    public void onPeriodicCheck(PeriodicCheckEvent event) {
        checkAndExecuteSingleAutomation(event.getAutomation(), event.getRecentData(), event.getTriggerSource());
    }

    @EventListener
    public void onCustomEvent(LiveEvent event) {
        var payload = event.getPayload();
        // FIX Bug#6: normalize device_id to lowercase on write so lookups match MongoDB IDs
        String rawDeviceId = payload.get("device_id").toString();
        String normalizedDeviceId = rawDeviceId.toLowerCase(Locale.ROOT);
        redisService.setRecentDeviceData(normalizedDeviceId, payload);
        // Also write with the original key for backward compat if different
        if (!normalizedDeviceId.equals(rawDeviceId)) {
            redisService.setRecentDeviceData(rawDeviceId, payload);
        }
    }

    private List<Automation> getCachedAutomationsForDevice(String deviceId) {
        String key = "AUTOMATIONS_BY_DEVICE:" + deviceId;
        try {
            Object cached = redisService.get(key);
            if (cached != null) {
                return objectMapper.readValue(
                        cached.toString(),
                        objectMapper.getTypeFactory()
                                .constructCollectionType(List.class, Automation.class));
            }
        } catch (Exception e) {
            log.warn("Automation list cache miss (parse error) for device {}: {}", deviceId, e.getMessage());
        }
        List<Automation> fresh = automationRepository.findByTrigger_DeviceId(deviceId);
        try {
            redisService.setWithExpiry(key, objectMapper.writeValueAsString(fresh), 300);
        } catch (Exception e) {
            log.warn("Failed to cache automation list for device {}: {}", deviceId, e.getMessage());
        }
        return fresh;
    }


    // ═════════════════════════════════════════════════════════════════════
    // SCHEDULED JOBS
    // ═════════════════════════════════════════════════════════════════════

    @Scheduled(fixedRate = 12_000)
    public void triggerPeriodicAutomations() {
        automationRepository.findEnabledForExecution().stream()
                .filter(a -> !scheduledAutomationManager.hasOnlyScheduledConditions(a))
                .forEach(a -> {
                    AutomationCache cached = redisService.getAutomationCache(
                            a.getTrigger().getDeviceId() + ":" + a.getId());
                    Automation toRun = (cached != null && cached.getAutomation() != null)
                            ? cached.getAutomation() : a;

                    Map<String, Object> recentData =
                            redisService.getRecentDeviceData(a.getTrigger().getDeviceId());

                    // FIX Bug#5: skip data-driven automations when primary device has no cached data,
                    // and warn per secondary source that has no Redis data either.
                    if ((recentData == null || recentData.isEmpty()) && hasDataDrivenCondition(toRun)) {
                        log.warn("⚠️ [{}] Skipping periodic run — primary device '{}' has no cached data in Redis. " +
                                        "Ensure the device is publishing live events.",
                                toRun.getName(), a.getTrigger().getDeviceId());
                        return;
                    }

                    // Warn about secondary devices with missing Redis data
                    if (toRun.getTrigger().getSources() != null) {
                        toRun.getTrigger().getSources().stream()
                                .filter(s -> !"primary".equals(s.getRole()))
                                .forEach(s -> {
                                    Map<String, Object> secData =
                                            redisService.getRecentDeviceData(s.getDeviceId());
                                    if (secData == null || secData.isEmpty()) {
                                        log.warn("⚠️ [{}] Secondary device '{}' has no cached data in Redis — " +
                                                        "conditions referencing it will return false.",
                                                toRun.getName(), s.getDeviceId());
                                    }
                                });
                    }

                    eventPublisher.publishEvent(new PeriodicCheckEvent(this, toRun, recentData));
                });
    }

    @Scheduled(fixedRate = 60_000 * 5)
    public void updateRedisStorage() {
        Date fiveMinutesAgo = new Date(System.currentTimeMillis() - 5 * 60 * 1000);
        automationRepository.findAll().forEach(a -> {
            String cacheKey = a.getTrigger().getDeviceId() + ":" + a.getId();
            AutomationCache existing = redisService.getAutomationCache(cacheKey);
            if (existing != null && existing.getLastUpdate() != null
                    && existing.getLastUpdate().after(fiveMinutesAgo)) return;

            redisService.setAutomationCache(cacheKey, AutomationCache.builder()
                    .id(a.getId()).automation(a)
                    .triggerDeviceType(a.getTriggerDeviceType())
                    .enabled(a.getIsEnabled())
                    .state(existing != null ? existing.getState() : AutomationState.IDLE)
                    .branchStates(existing != null ? existing.getBranchStates() : new HashMap<>())
                    .triggerDeviceId(a.getTrigger().getDeviceId())
                    .isActive(existing != null && Boolean.TRUE.equals(existing.getIsActive()))
                    .triggeredPreviously(existing != null && existing.isTriggeredPreviously())
                    .previousExecutionTime(existing != null ? existing.getPreviousExecutionTime() : null)
                    .lastUpdate(existing != null ? existing.getLastUpdate() : null)
                    .build());
        });
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
                                                String savedBy,
                                                String changeNote) {
        String result = saveAutomationDetailInternal(detail);
        if ("success".equals(result)) {
            automationRepository.findById(detail.getId()).ifPresent(a ->
                    automationVersionService.snapshot(a, detail, savedBy, changeNote));
        }
        return result;
    }

    private String saveAutomationDetailInternal(AutomationDetail detail) {
        log.info("Saving automation detail: {}", detail);
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
                            t.getName(), t.getPriority(), t.getNodeId(), t.getSources()));
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
                                c.getDurationMinutes(), c.isEnabled(), c.getPreviousNodeRef(), c.getDeviceId()))
                        .collect(Collectors.toList()));

        automationBuilder.operators(
                detail.getNodes().stream()
                        .map(n -> n.getData().getOperators())
                        .filter(Objects::nonNull)
                        .map(c -> new Automation.Operator(
                                c.getType(), c.getLogicType(),
                                c.getPreviousNodeRef(), c.getNodeId(),
                                c.getPriority()))
                        .collect(Collectors.toList()));

        var automation = automationBuilder.build();
        List<String> subscribers = automation.getTrigger().getSources().stream()
                .filter(s -> "primary".equals(s.getRole()))
                .map(TriggerSource::getDeviceId)
                .toList();
        automation.setSubscriberDeviceIds(subscribers);
        automation.setTriggerDeviceType(mainService.getDevice(automation.getTrigger().getDeviceId()).getType());

        var saved = automationRepository.save(automation);
        detail.setId(saved.getId());
        detail.setUpdateDate(new Date());
        automationDetailRepository.save(detail);

        notificationService.sendNotification("Automation saved successfully", "success");
        refreshCacheForAutomation(saved);
        automationVersionService.snapshot(saved, detail, "system", null);

        return "success";
    }

    private boolean hasDataDrivenCondition(Automation automation) {
        if (automation.getConditions() == null) return false;
        Set<String> operatorIds = automation.getOperators() == null ? Set.of() :
                automation.getOperators().stream()
                .map(Automation.Operator::getNodeId)
                .collect(Collectors.toSet());
        return automation.getConditions().stream()
                .filter(Automation.Condition::isEnabled)
                .filter(c -> !automationEngine.isGateCondition(c, operatorIds))
                .anyMatch(c -> !"scheduled".equals(c.getCondition()));
    }


    // ═════════════════════════════════════════════════════════════════════
    // CACHE REFRESH
    // ═════════════════════════════════════════════════════════════════════

    private void refreshCacheForAutomation(Automation automation) {
        automation.getConditions().forEach(c -> {
            redisService.delete("INTERVAL:" + automation.getId() + ":" + c.getNodeId());
            redisService.delete("RUNNING:" + automation.getId() + ":" + c.getNodeId());
        });
        String cacheKey = automation.getTrigger().getDeviceId() + ":" + automation.getId();
        try {
            AutomationCache existing = redisService.getAutomationCache(cacheKey);
            redisService.setAutomationCache(cacheKey, AutomationCache.builder()
                    .id(automation.getId()).automation(automation)
                    .triggerDeviceType(automation.getTriggerDeviceType())
                    .enabled(automation.getIsEnabled())
                    .state(existing != null ? existing.getState() : AutomationState.IDLE)
                    .branchStates(existing != null ? existing.getBranchStates() : new HashMap<>())
                    .triggerDeviceId(automation.getTrigger().getDeviceId())
                    .isActive(existing != null && Boolean.TRUE.equals(existing.getIsActive()))
                    .triggeredPreviously(existing != null && existing.isTriggeredPreviously())
                    .previousExecutionTime(existing != null ? existing.getPreviousExecutionTime() : null)
                    .lastUpdate(new Date())
                    .build());
            log.info("🔄 Cache refreshed: {}", automation.getName());
            scheduledAutomationManager.refresh(automation);
        } catch (Exception e) {
            log.warn("⚠️ Cache refresh failed for '{}': {}", automation.getName(), e.getMessage());
        }
    }


    // ═════════════════════════════════════════════════════════════════════
    // CORE EXECUTION
    // ═════════════════════════════════════════════════════════════════════

    public void checkAndExecuteSingleAutomation(Automation automation,
                                                Map<String, Object> data,
                                                String user) {

        if (!featureService.isFeatureEnabled("PERIODIC_AUTOMATION_SERVICE")) {
            log.error("Automation service is currently disabled.");
            if ("user".equals(user)) {
                notificationService.sendNotification("Automation Service is disabled.", "info");
            }
            return;
        }
        Date now = new Date();
        String deviceId = automation.getTrigger().getDeviceId();
        String cacheKey = deviceId + ":" + automation.getId();

        String lockKey = "EXEC_LOCK:" + automation.getId();
        String lockValue = UUID.randomUUID().toString();
        boolean lockAcquired = redisService.setIfAbsent(lockKey, lockValue, LOCK_TTL_SECONDS);

        var logBuilder = AutomationLog.builder()
                .automationId(automation.getId())
                .user(user)
                .automationName(automation.getName())
                .payload(data != null ? data : Map.of())
                .triggerDeviceId(deviceId)
                .timestamp(now);

        if (!lockAcquired) {
            log.debug("⏭️ Skipping {} — lock held", automation.getName());
            saveLog(logBuilder.status(AutomationLog.LogStatus.SKIPPED)
                    .reason("Execution lock held by concurrent caller").build());
            return;
        }

        try {
            if (redisService.exists("SNOOZE:" + automation.getId())) {
                long rem = redisService.getTTL("SNOOZE:" + automation.getId());
                saveLog(logBuilder.status(AutomationLog.LogStatus.SKIPPED)
                        .snoozeState("SNOOZED")
                        .reason("Snoozed — " + rem / 60 + " min remaining").build());
                return;
            }
            if (redisService.exists("TIMED_DISABLE:" + automation.getId())) {
                long rem = redisService.getTTL("TIMED_DISABLE:" + automation.getId());
                saveLog(logBuilder.status(AutomationLog.LogStatus.SKIPPED)
                        .reason("Timed-disabled — " + rem / 60 + " min remaining").build());
                return;
            }

            Map<String, Object> payload = new HashMap<>();
            if (data != null) payload.putAll(data);
            else payload.putAll(mainService.getLastData(deviceId));

            AutomationCache cache = loadOrInitCache(cacheKey, automation);

            ExecutionContext ctx = new ExecutionContext();
            NodeResult rootResult = automationEngine.evaluate(automation, payload, ctx, cache);

            // ── Get all trigger-side conditions (root + chained) ──────────
            // FIX: use automationEngine.getTriggerConditions() which includes chained
            // conditions like node_condition_11, not just root-level ones.
            List<Automation.Condition> triggerConditions =
                    automationEngine.getTriggerConditions(automation);

            // ── Stateless "none" actions ──────────────────────────────────
            List<Automation.Action> noneActions = automation.getActions().stream()
                    .filter(a -> Boolean.TRUE.equals(a.getIsEnabled()))
                    .filter(a -> "none".equalsIgnoreCase(a.getConditionGroup()))
                    .filter(a -> a.getPreviousNodeRef() != null
                            && a.getPreviousNodeRef().stream()
                            .anyMatch(ref -> ctx.getTrueNodes().contains(ref.getNodeId())))
                    .sorted(Comparator.comparingInt(a -> a.getOrder() != 0 ? a.getOrder() : Integer.MAX_VALUE))
                    .toList();

            if (!noneActions.isEmpty()) {
                log.info("⚡ [{}] [{}] Firing {} stateless action(s)",
                        user, automation.getName(), noneActions.size());
                executeWithTimeout(noneActions, payload, user, automation.getId(), automation.getName());
                List<AutomationLog.ConditionResult> conditionResults =
                        buildConditionResults(automation, ctx, payload);
                saveLog(logBuilder
                        .conditionResults(conditionResults)
                        .status(AutomationLog.LogStatus.TRIGGERED)
                        .reason("Stateless actions fired: " + actionSummary(noneActions))
                        .build());
                return;
            }

            Set<String> operatorIds = automation.getOperators() == null ? Set.of() :
                    automation.getOperators().stream()
                    .map(Automation.Operator::getNodeId)
                    .collect(Collectors.toSet());

            // ── Phase 1: c1 (all trigger-side conditions must be true) ────
            boolean c1True = rootResult != null && rootResult.isTrue();

            if (!c1True) {
                List<Automation.Action> informational = resolveInformationalActions(automation, ctx);
                if (!informational.isEmpty()) {
                    log.info("ℹ️ [{}] [{}] trigger condition not met — firing {} informational action(s)",
                            user, automation.getName(), informational.size());
                    executeWithTimeout(informational, payload, user, automation.getId(), automation.getName());
                }

                boolean anyWasActive = automation.getConditions().stream()
                        .filter(Automation.Condition::isEnabled)
                        .filter(c -> automationEngine.isGateCondition(c, operatorIds))
                        .anyMatch(c -> {
                            AutomationState s = cache.getBranchState(c.getNodeId());
                            return s == AutomationState.ACTIVE || s == AutomationState.HOLDING;
                        });

                if (anyWasActive) {
                    List<Automation.Action> c1NegActions =
                            resolveC1NegativeActions(automation, triggerConditions);
                    if (!c1NegActions.isEmpty()) {
                        log.info("⏹️ [{}] [{}] trigger condition false, reverting all branches — {}",
                                user, automation.getName(), actionSummary(c1NegActions));
                        executeWithTimeout(c1NegActions, payload, user, automation.getId(), automation.getName())
                                .thenAccept(ok -> {
                                    if (Boolean.TRUE.equals(ok)) {
                                        automation.getConditions().stream()
                                                .filter(Automation.Condition::isEnabled)
                                                .filter(c -> automationEngine.isGateCondition(c, operatorIds))
                                                .forEach(c -> {
                                                    cache.setBranchState(c.getNodeId(), AutomationState.IDLE);
                                                    redisService.delete("RUNNING:" + automation.getId()
                                                            + ":" + c.getNodeId());
                                                });
                                        cache.setLastUpdate(new Date());
                                        redisService.setAutomationCache(cacheKey, cache);
                                        notificationService.sendNotification(
                                                automation.getName() + " — condition lost", "info");
                                    }
                                });
                    }
                }

                saveLog(logBuilder.status(AutomationLog.LogStatus.TRIGGER_FALSE)
                        .reason("Trigger condition false"
                                + (anyWasActive ? " — branches reset, c1-negative actions fired" : ""))
                        .build());
                return;
            }

            // ── Phase 2: resolve gate branches ────────────────────────────
            List<GateBranch> branches = resolveGateBranches(automation, ctx);

            if (branches.isEmpty()) {
                handleNoBranchAutomation(automation, ctx, payload, cache,
                        cacheKey, user, logBuilder, now);
                return;
            }

            List<GateBranch> trueBranches = branches.stream()
                    .filter(b -> {
                        NodeResult nr = ctx.get(b.gateNodeId());
                        return nr != null && nr.isTrue();
                    })
                    .toList();

            GateBranch winner = trueBranches.isEmpty() ? null : trueBranches.get(0);

            List<CompletableFuture<Void>> tickFutures = new ArrayList<>();
            boolean anyBranchWasOrIsActive = false;

            for (GateBranch branch : branches) {
                boolean gateTrue = trueBranches.contains(branch);
                boolean isWinner = branch == winner;
                AutomationState branchState = cache.getBranchState(branch.gateNodeId());

                if (branchState == AutomationState.ACTIVE
                        || branchState == AutomationState.HOLDING) {

                    if (!gateTrue || !isWinner) {
                        String reason = !gateTrue
                                ? "Condition no longer met (" + describeBranch(branch) + ")"
                                : "Overridden by '" + describeBranch(winner) + "'";
                        String revertMsg = !gateTrue
                                ? "⏹️ " + automation.getName() + " — " + describeBranch(branch) + " ended"
                                : "⏹️ " + automation.getName() + " — " + describeBranch(branch)
                                  + " overridden by " + describeBranch(winner);

                        log.info("⏹️ [{}] [{}] '{}' reverting — {}", user,
                                automation.getName(), describeBranch(branch), revertMsg);

                        final AutomationCache finalCache = cache;
                        final GateBranch fb = branch;

                        CompletableFuture<Void> revertFuture =
                                executeWithTimeout(branch.negativeActions(), payload,
                                        user, automation.getId(), automation.getName())
                                        .thenAccept(ok -> {
                                            if (Boolean.TRUE.equals(ok)) {
                                                finalCache.setBranchState(
                                                        fb.gateNodeId(), AutomationState.IDLE);
                                                finalCache.setLastUpdate(new Date());
                                                redisService.setAutomationCache(cacheKey, finalCache);
                                                redisService.delete("RUNNING:" + automation.getId()
                                                        + ":" + fb.gateNodeId());
                                                notificationService.sendNotification(
                                                        automation.getName() + " — " + reason, "info");
                                                log.info("🔁 [{}] [{}] '{}' → IDLE — {}", user,
                                                        automation.getName(), describeBranch(fb), reason);
                                            }
                                        });
                        tickFutures.add(revertFuture);
                    } else {
                        anyBranchWasOrIsActive = true;
                    }

                } else if (branchState == AutomationState.IDLE) {

                    if (gateTrue && isWinner) {
                        Automation.Condition gc = branch.gateCondition();
                        if ("interval".equals(gc.getScheduleType())
                                && gc.getIntervalMinutes() > 0
                                && cache.getPreviousExecutionTime() != null) {

                            long secondsSinceLast =
                                    (now.getTime() - cache.getPreviousExecutionTime().getTime()) / 1000;
                            long intervalSec = gc.getIntervalMinutes() * 60L;
                            if (secondsSinceLast < intervalSec) {
                                log.debug("⏳ [{}] [{}] '{}' — interval cooldown, {}s until next run",
                                        user, automation.getName(), describeBranch(branch),
                                        intervalSec - secondsSinceLast);
                                continue;
                            }
                        }

                        final AutomationCache finalCache = cache;
                        final GateBranch fb = branch;

                        log.info("🚀 [{}] [{}] Branch '{}' triggering — {} (priority {})", user,
                                automation.getName(), branch.gateNodeId(),
                                describeBranch(branch), branch.priority());

                        CompletableFuture<Void> triggerFuture =
                                executeWithTimeout(branch.positiveActions(), payload,
                                        user, automation.getId(), automation.getName())
                                        .thenAccept(ok -> {
                                            if (!Boolean.TRUE.equals(ok)) {
                                                log.warn("⚠️ [{}] [{}] '{}' — execution failed", user,
                                                        automation.getName(), describeBranch(fb));
                                                return;
                                            }
                                            boolean hasDuration =
                                                    fb.gateCondition().getDurationMinutes() > 0;
                                            boolean hasNegatives = !branch.negativeActions().isEmpty();

                                            AutomationState nextState = hasDuration
                                                    ? AutomationState.HOLDING
                                                    : AutomationState.ACTIVE;

                                            finalCache.setBranchState(fb.gateNodeId(), nextState);
                                            finalCache.setTriggeredPreviously(true);
                                            finalCache.setPreviousExecutionTime(now);
                                            finalCache.setLastUpdate(new Date());

                                            if (hasDuration) {
                                                String runKey = "RUNNING:" + automation.getId()
                                                        + ":" + fb.gateNodeId();
                                                redisService.setWithExpiry(runKey, "active",
                                                        fb.gateCondition().getDurationMinutes() * 60L);
                                                log.info("⏱️ [{}] [{}] '{}' — active for {} min", user,
                                                        automation.getName(), describeBranch(fb),
                                                        fb.gateCondition().getDurationMinutes());
                                            } else if (hasNegatives) {
                                                finalCache.setBranchState(fb.gateNodeId(), AutomationState.ACTIVE);
                                            }

                                            redisService.setAutomationCache(cacheKey, finalCache);
                                            notificationService.sendNotification(
                                                    automation.getName() + " triggered", "success");
                                        });
                        tickFutures.add(triggerFuture);
                        anyBranchWasOrIsActive = true;

                    } else if (gateTrue || !isWinner) {
                        log.debug("⏸️ [{}] [{}] '{}' suppressed by '{}'", user,
                                automation.getName(), describeBranch(branch),
                                winner != null ? describeBranch(winner) : "?");
                        saveLog(AutomationLog.builder()
                                .automationId(automation.getId())
                                .automationName(automation.getName())
                                .user(user)
                                .triggerDeviceId(deviceId)
                                .timestamp(now)
                                .status(AutomationLog.LogStatus.SUPPRESSED)
                                .reason(describeBranch(branch) + " matched but suppressed — "
                                        + (winner != null ? describeBranch(winner) : "unknown")
                                        + " has higher priority ("
                                        + (winner != null ? winner.priority() : "?") + ")")
                                .build());
                    }
                }

                // ── HOLDING: check if duration timer expired ──────────────
                if (branchState == AutomationState.HOLDING) {
                    String runKey = "RUNNING:" + automation.getId() + ":" + branch.gateNodeId();
                    if (!redisService.exists(runKey)) {
                        final AutomationCache finalCache = cache;
                        final GateBranch fb = branch;

                        log.info("⏱️ [{}] [{}] '{}' — duration expired, reverting", user,
                                automation.getName(), describeBranch(branch));

                        CompletableFuture<Void> expiryRevert =
                                executeWithTimeout(branch.negativeActions(), payload,
                                        user, automation.getId(), automation.getName())
                                        .thenAccept(ok -> {
                                            if (Boolean.TRUE.equals(ok)) {
                                                finalCache.setBranchState(
                                                        fb.gateNodeId(), AutomationState.IDLE);
                                                finalCache.setLastUpdate(new Date());
                                                redisService.setAutomationCache(cacheKey, finalCache);
                                                notificationService.sendNotification(
                                                        automation.getName() + " — "
                                                                + describeBranch(fb) + " timer expired", "info");
                                                log.info("🔁 [{}] [{}] '{}' → IDLE (timer expired)", user,
                                                        automation.getName(), describeBranch(fb));
                                            }
                                        });
                        tickFutures.add(expiryRevert);
                    } else {
                        anyBranchWasOrIsActive = true;
                    }
                }
            }

            // ── Phase 3: c1 true but no branch fired or active ────────────
            // Phase 3: c1 true but no branch fired or is active
            if (!anyBranchWasOrIsActive && winner == null) {

                // 3a: any branch was previously ACTIVE but its gate just turned false
                //     AND it wasn't already handled in the branch loop (edge case: branch
                //     was ACTIVE but gateTrue was also false — already reverted above).
                //     Nothing extra to do here — the branch loop already fired negativeActions.

                // 3b: no branch has ever been active — lux in range but outside all windows.
                //     Check for explicitly-tagged fallback actions (conditionGroup="fallback").
                List<Automation.Action> explicitFallback = automation.getActions().stream()
                        .filter(a -> Boolean.TRUE.equals(a.getIsEnabled()))
                        .filter(a -> "fallback".equalsIgnoreCase(a.getConditionGroup()))
                        .sorted(Comparator.comparingInt(a -> a.getOrder() != 0 ? a.getOrder() : Integer.MAX_VALUE))
                        .toList();

                if (!explicitFallback.isEmpty()) {
                    log.info("ℹ️ [{}] [{}] c1 true, no gate branch matched — firing explicit fallback",
                            user, automation.getName());
                    executeWithTimeout(explicitFallback, payload, user, automation.getId(), automation.getName());
                    saveLog(logBuilder.status(AutomationLog.LogStatus.TRIGGER_FALSE)
                            .reason("c1 true, no gate branch matched — explicit fallback fired").build());
                } else {
                    saveLog(logBuilder.status(AutomationLog.LogStatus.NOT_MET)
                            .reason("c1 true but no gate branch matched — no fallback defined").build());
                }
                return;
            }

            List<AutomationLog.ConditionResult> conditionResults =
                    buildConditionResults(automation, ctx, payload);
            logBuilder.conditionResults(conditionResults).payload(payload);

            // FIX Bug#1 + Bug#2: call shadowEvaluate() INSIDE allOf().thenRun() so
            // finalAnyBranchWasOrIsActive is resolved only after all futures complete.
            final boolean finalAnyBranchWasOrIsActive = anyBranchWasOrIsActive;
            final String winnerDesc = winner != null ? describeBranch(winner) : null;
            final List<AutomationLog.ConditionResult> finalConditionResults = conditionResults;

            CompletableFuture.allOf(tickFutures.toArray(new CompletableFuture[0]))
                    .thenRun(() -> {
                        saveLog(logBuilder
                                .status(finalAnyBranchWasOrIsActive
                                        ? AutomationLog.LogStatus.TRIGGERED
                                        : AutomationLog.LogStatus.SKIPPED)
                                .reason("Branch evaluation complete").build());

                        // FIX Bug#1 + Bug#2: shadow evaluate AFTER variant A fully resolves
                        abTestService.shadowEvaluate(
                                automation.getId(),
                                payload,
                                finalAnyBranchWasOrIsActive,
                                finalConditionResults,
                                winnerDesc);
                    });

        } catch (Exception e) {
            log.error("❌ Error in automation {}: {}", automation.getName(), e.getMessage(), e);
            notificationService.sendNotification(automation.getName() + " error", "error");
            saveLog(logBuilder.status(AutomationLog.LogStatus.ERROR)
                    .reason("Execution failed: " + e.getMessage()).build());
        } finally {
            releaseLock(lockKey, lockValue);
        }
    }

    private void handleNoBranchAutomation(Automation automation,
                                          ExecutionContext ctx,
                                          Map<String, Object> payload,
                                          AutomationCache cache,
                                          String cacheKey,
                                          String user,
                                          AutomationLog.AutomationLogBuilder logBuilder,
                                          Date now) {
        NodeResult root = automationEngine.findRootResult(automation, ctx);
        boolean conditionNow = root != null && root.isTrue();

        AutomationState currentState = resolveState(cache.getState());

        List<Automation.Action> positiveActions = resolveActionsForGroup(automation, ctx, "positive");
        List<Automation.Action> negativeActions = resolveActionsForGroup(automation, ctx, "negative");

        if (currentState == AutomationState.IDLE && conditionNow) {
            final AutomationCache fc = cache;
            boolean hasNegativeActions = !negativeActions.isEmpty();

            executeWithTimeout(positiveActions, payload, user, automation.getId(), automation.getName())
                    .thenAccept(ok -> {
                        if (!Boolean.TRUE.equals(ok)) return;
                        if (hasNegativeActions) fc.setState(AutomationState.ACTIVE);
                        fc.setTriggeredPreviously(true);
                        fc.setPreviousExecutionTime(now);
                        fc.setLastUpdate(new Date());
                        redisService.setAutomationCache(cacheKey, fc);
                        notificationService.sendNotification(automation.getName() + " triggered", "success");
                    });
            logBuilder.status(AutomationLog.LogStatus.TRIGGERED)
                    .reason((hasNegativeActions ? "Stateful" : "Stateless")
                            + " — condition met, " + actionSummary(positiveActions) + " fired");

        } else if ((currentState == AutomationState.ACTIVE
                || currentState == AutomationState.HOLDING) && !conditionNow) {
            final AutomationCache fc = cache;
            executeWithTimeout(negativeActions, payload, user, automation.getId(), automation.getName())
                    .thenAccept(ok -> {
                        if (!Boolean.TRUE.equals(ok)) return;
                        fc.setState(AutomationState.IDLE);
                        fc.setTriggeredPreviously(false);
                        fc.setLastUpdate(new Date());
                        redisService.setAutomationCache(cacheKey, fc);
                        notificationService.sendNotification(
                                automation.getName() + " — condition cleared", "info");
                    });
            logBuilder.status(AutomationLog.LogStatus.RESTORED)
                    .reason("Condition cleared — " + actionSummary(negativeActions) + " fired");

        } else if (currentState == AutomationState.IDLE) {
            logBuilder.status(AutomationLog.LogStatus.NOT_MET)
                    .reason("Condition not satisfied");
        } else {
            logBuilder.status(AutomationLog.LogStatus.SKIPPED)
                    .reason("No state change (state=" + currentState + ", conditionNow=" + conditionNow + ")");
        }
        saveLog(logBuilder.build());
    }

    public void executeSceneActions(Automation automation,
                                    List<Automation.Action> actions,
                                    Map<String, Object> payload,
                                    String user) {
        executeWithTimeout(actions, payload, user, automation.getId(), automation.getName());
    }


    // ═════════════════════════════════════════════════════════════════════
    // BRANCH RESOLUTION HELPERS
    // ═════════════════════════════════════════════════════════════════════

    private List<GateBranch> resolveGateBranches(Automation automation, ExecutionContext ctx) {
        if (automation.getOperators() == null || automation.getOperators().isEmpty())
            return List.of();

        Set<String> operatorIds = automation.getOperators().stream()
                .map(Automation.Operator::getNodeId)
                .collect(Collectors.toSet());

        return automation.getConditions().stream()
                .filter(Automation.Condition::isEnabled)
                .filter(c -> automationEngine.isGateCondition(c, operatorIds))
                .map(gateCondition -> {
                    String opNodeId = gateCondition.getPreviousNodeRef().stream()
                            .filter(ref -> operatorIds.contains(ref.getNodeId()))
                            .map(NodeRef::getNodeId)
                            .findFirst()
                            .orElse(null);

                    if (opNodeId == null) return null;

                    Automation.Operator op = automation.getOperators().stream()
                            .filter(o -> o.getNodeId().equals(opNodeId))
                            .findFirst().orElse(null);

                    if (op == null) return null;

                    return new GateBranch(
                            op,
                            gateCondition,
                            resolveActionsForNode(automation, ctx, gateCondition.getNodeId(), "positive"),
                            resolveActionsForNode(automation, ctx, gateCondition.getNodeId(), "negative")
                    );
                })
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(GateBranch::priority).reversed())
                .toList();
    }

    public List<Automation.Action> resolveActionsForNode(
            Automation automation,
            ExecutionContext ctx,
            String nodeId,
            String group) {

        Set<String> trueNodes = ctx.getTrueNodes();
        Set<String> falseNodes = ctx.getFalseNodes();

        return automation.getActions().stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsEnabled()))
                .filter(a -> group.equalsIgnoreCase(a.getConditionGroup()))
                .filter(a -> {
                    if (a.getPreviousNodeRef() == null || a.getPreviousNodeRef().isEmpty())
                        return false;
                    return a.getPreviousNodeRef().stream().anyMatch(ref -> {
                        if (!ref.getNodeId().equals(nodeId)) return false;
                        String handle = ref.getHandle();
                        if (handle != null && handle.contains("cond-negative"))
                            return falseNodes.contains(ref.getNodeId());
                        return trueNodes.contains(ref.getNodeId()) || (handle == null);
                    });
                })
                .sorted(Comparator.comparingInt(a -> a.getOrder() != 0 ? a.getOrder() : Integer.MAX_VALUE))
                .toList();
    }

    private List<Automation.Action> resolveInformationalActions(
            Automation automation, ExecutionContext ctx) {
        return automation.getActions().stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsEnabled()))
                .filter(a -> "informational".equalsIgnoreCase(a.getConditionGroup()))
                .sorted(Comparator.comparingInt(a -> a.getOrder() != 0 ? a.getOrder() : Integer.MAX_VALUE))
                .toList();
    }
    

    /**
     * c1-negative revert: fires when c1 turns FALSE and at least one branch was ACTIVE.
     * Picks "negative" actions whose previousNodeRef points to a TRIGGER condition node.
     * For this automation: node_action_14 (refs node_condition_11) and
     * node_action_16/node_action_13 (refs node_condition_5) are correctly picked up here.
     * <p>
     * FIX Bug#4 (duplicate actions): deduplicate by deviceId+key+data so the same
     * device command isn't sent twice when multiple actions resolve to the same payload.
     */
    private List<Automation.Action> resolveC1NegativeActions(
            Automation automation,
            List<Automation.Condition> triggerConditions) {

        Set<String> triggerNodeIds = triggerConditions.stream()
                .map(Automation.Condition::getNodeId)
                .collect(Collectors.toSet());

        List<Automation.Action> raw = automation.getActions().stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsEnabled()))
                .filter(a -> "negative".equalsIgnoreCase(a.getConditionGroup()))
                .filter(a -> a.getPreviousNodeRef() != null
                        && a.getPreviousNodeRef().stream().anyMatch(ref ->
                        triggerNodeIds.contains(ref.getNodeId())))
                .sorted(Comparator.comparingInt(a -> a.getOrder() != 0 ? a.getOrder() : Integer.MAX_VALUE))
                .toList();

        // Deduplicate: keep first occurrence per (deviceId, key, data) triple
        Set<String> seen = new LinkedHashSet<>();
        return raw.stream()
                .filter(a -> seen.add(a.getDeviceId() + "|" + a.getKey() + "|" + a.getData()))
                .collect(Collectors.toList());
    }

    public List<Automation.Action> resolveActionsForGroup(
            Automation automation, ExecutionContext ctx, String group) {

        Set<String> trueNodes = ctx.getTrueNodes();
        Set<String> falseNodes = ctx.getFalseNodes();

        return automation.getActions().stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsEnabled()))
                .filter(a -> group.equalsIgnoreCase(a.getConditionGroup()))
                .filter(a -> {
                    if (a.getPreviousNodeRef() == null || a.getPreviousNodeRef().isEmpty())
                        return true;
                    return a.getPreviousNodeRef().stream().anyMatch(ref -> {
                        String handle = ref.getHandle();
                        if (handle != null && handle.contains("cond-negative"))
                            return falseNodes.contains(ref.getNodeId());
                        return trueNodes.contains(ref.getNodeId());
                    });
                })
                .sorted(Comparator.comparingInt(a -> a.getOrder() != 0 ? a.getOrder() : Integer.MAX_VALUE))
                .toList();
    }


    // ═════════════════════════════════════════════════════════════════════
    // IMMEDIATE / OVERRIDE EXECUTION
    // ═════════════════════════════════════════════════════════════════════

    private void executeAutomationImmediate(Automation automation,
                                            Map<String, Object> payload,
                                            String user) {
        if (!automation.getIsEnabled()) return;

        var logBuilder = AutomationLog.builder()
                .automationId(automation.getId()).automationName(automation.getName())
                .conditionResults(new ArrayList<>()).operatorLogic("")
                .payload(payload).triggerType(automation.getTrigger().getType())
                .triggerDeviceId(automation.getTrigger().getDeviceId())
                .timestamp(new Date());

        executeWithTimeout(automation.getActions(), payload, user, automation.getId(), automation.getName())
                .thenAccept(success -> saveLog(logBuilder
                        .status(Boolean.TRUE.equals(success)
                                ? AutomationLog.LogStatus.USER_OVERRIDE
                                : AutomationLog.LogStatus.ERROR)
                        .reason(Boolean.TRUE.equals(success)
                                ? "Triggered manually by user: " + user
                                : "User override FAILED for user: " + user)
                        .build()))
                .exceptionally(ex -> {
                    saveLog(logBuilder.status(AutomationLog.LogStatus.ERROR)
                            .reason("User override FAILED: " + ex.getMessage()).build());
                    return null;
                });
    }


    // ═════════════════════════════════════════════════════════════════════
    // ACTION EXECUTION
    // ═════════════════════════════════════════════════════════════════════

    private CompletableFuture<Boolean> executeWithTimeout(List<Automation.Action> actions,
                                                          Map<String, Object> payload,
                                                          String user,
                                                          String automationId,
                                                          String automationName) {
        return executeActionsInternal(actions, user, payload, automationId, automationName)
                .orTimeout(AUTOMATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    if (cause instanceof TimeoutException)
                        log.error("⏱️ Timeout: {}", automationName);
                    else
                        log.error("❌ Async error in {}: {}", automationName,
                                cause.getMessage(), cause);
                    return false;
                });
    }

    private CompletableFuture<Boolean> executeActionsInternal(List<Automation.Action> actions,
                                                              String user,
                                                              Map<String, Object> payload,
                                                              String automationId,
                                                              String automationName) {
        CompletableFuture<Boolean> chain = CompletableFuture.completedFuture(true);

        for (Automation.Action action : actions) {
            chain = chain.thenCompose(previousOk -> {
                CompletableFuture<Boolean> actionFuture =
                        CompletableFuture.supplyAsync(() -> {
                            try {
                                log.info("▶️ [{}] Executing action: {} (order={})",
                                        automationName, action.getName(), action.getOrder());
                                executeSingleAction(action, user, payload, automationId, automationName);
                                return true;
                            } catch (Exception e) {
                                log.error("❌ [{}] Failed action: {}",
                                        automationName, action.getName(), e);
                                return false;
                            }
                        }, automationExecutor);

                int delaySec = action.getDelaySeconds();
                if (delaySec > 0) {
                    return actionFuture.thenCompose(ok -> {
                        CompletableFuture<Boolean> delayed = new CompletableFuture<>();
                        delayScheduler.schedule(() -> delayed.complete(ok),
                                delaySec, TimeUnit.SECONDS);
                        return delayed;
                    });
                }
                return actionFuture;
            });
        }
        return chain;
    }

    private void executeSingleAction(Automation.Action action,
                                     String user,
                                     Map<String, Object> value,
                                     String automationId,
                                     String automationName) {
        Object parsedData = parseData(action.getData());
        Map<String, Object> payload = Map.of(action.getKey(), parsedData, "key", action.getKey());

        if ("alert".equals(action.getKey())) {
            notificationService.sendAlert(
                    "Alert: " + action.getData().toUpperCase(Locale.ROOT), action.getData());

        } else if ("app_notify".equals(action.getKey())) {
            notificationService.sendNotify("Automation",
                    action.getData() + " and live data are " + value, "low");

        } else if ("WLED".equals(mainService.getDevice(action.getDeviceId()).getType())) {
            handleWLED(action.getDeviceId(), new HashMap<>(payload), user);

        } else {
            String correlationId = UUID.randomUUID().toString();
            Map<String, Object> trackedPayload = new HashMap<>(payload);
            trackedPayload.put("_cid", correlationId);

            deviceActionStateRepository.save(DeviceActionState.builder()
                    .user(user).deviceId(action.getDeviceId())
                    .timestamp(new Date()).payload(trackedPayload).deviceType("sensor").build());
            messagingTemplate.convertAndSend("action/" + action.getDeviceId(), trackedPayload);
            sendToTopic("action/" + action.getDeviceId(), trackedPayload);

            String deviceName = automationEngine.resolveDeviceLabel(action.getDeviceId(), action.getName());
            deliveryTracker.register(correlationId, automationId, automationName,
                    action.getDeviceId(), deviceName, trackedPayload);
        }
    }


    // ═════════════════════════════════════════════════════════════════════
    // CONDITION RESULT BUILDER
    // ═════════════════════════════════════════════════════════════════════

    private List<AutomationLog.ConditionResult> buildConditionResults(
            Automation automation, ExecutionContext ctx, Map<String, Object> payload) {

        List<AutomationLog.ConditionResult> results = new ArrayList<>();

        Set<String> operatorIds = automation.getOperators() == null ? Set.of() :
                automation.getOperators().stream()
                .map(Automation.Operator::getNodeId).collect(Collectors.toSet());

        for (Automation.Condition c : automation.getConditions()) {
            NodeResult nr = ctx.get(c.getNodeId());
            if (nr == null) continue;

            String parentOpId = null;
            if (c.getPreviousNodeRef() != null) {
                parentOpId = c.getPreviousNodeRef().stream()
                        .map(NodeRef::getNodeId)
                        .filter(operatorIds::contains)
                        .findFirst().orElse(null);
            }

            var builder = AutomationLog.ConditionResult.builder()
                    .conditionNodeId(c.getNodeId())
                    .conditionType(c.getCondition())
                    .triggerKey(c.getTriggerKey())
                    .passed(nr.isTrue())
                    .isGateCondition(parentOpId != null)
                    .operatorNodeId(parentOpId);

            if ("scheduled".equals(c.getCondition())) {
                String schedType = c.getScheduleType() != null ? c.getScheduleType() : "exact";
                String expected = switch (schedType) {
                    case "range" -> c.getFromTime() + " – " + c.getToTime();
                    case "solar" -> c.getSolarType() + " +" + c.getOffsetMinutes() + "min";
                    case "interval" -> "every " + c.getIntervalMinutes() + "min";
                    default -> c.getTime();
                };
                builder.conditionType("scheduled/" + schedType)
                        .triggerKey("schedule")
                        .expectedValue(expected)
                        .actualValue(LocalTime.now(ZoneId.of("Asia/Kolkata")).toString())
                        .detail(nr.isTrue() ? "Schedule matched" : "Outside schedule window")
                        .days(c.getDays());

            } else if (c.getTriggerKey() != null && payload.containsKey(c.getTriggerKey())) {
                String raw = payload.get(c.getTriggerKey()).toString();
                String expected = switch (c.getCondition()) {
                    case "above" -> "> " + c.getValue();
                    case "below" -> "< " + c.getValue();
                    case "range" -> c.getAbove() + " < x < " + c.getBelow();
                    default -> c.getValue();
                };
                String detail = switch (c.getCondition()) {
                    case "above" -> raw + " > " + c.getValue()
                            + " → " + (nr.isTrue() ? "PASS" : "FAIL");
                    case "below" -> raw + " < " + c.getValue()
                            + " → " + (nr.isTrue() ? "PASS" : "FAIL");
                    case "range" -> raw + " in (" + c.getAbove() + ", " + c.getBelow() + ")"
                            + " → " + (nr.isTrue() ? "PASS" : "FAIL");
                    default -> raw + " == " + c.getValue()
                            + " → " + (nr.isTrue() ? "PASS" : "FAIL");
                };
                builder.actualValue(raw).expectedValue(expected).detail(detail);
            } else {
                builder.actualValue("missing").expectedValue(c.getValue())
                        .detail("Key '" + c.getTriggerKey() + "' not present in payload");
            }
            results.add(builder.build());
        }

        if (automation.getOperators() != null) {
            for (Automation.Operator op : automation.getOperators()) {
                NodeResult nr = ctx.get(op.getNodeId());
                if (nr == null) continue;

                List<String> inputIds = op.getPreviousNodeRef().stream()
                        .map(NodeRef::getNodeId).toList();
                List<String> inputResults = inputIds.stream()
                        .map(id -> id + "=" + (ctx.get(id) != null ? ctx.get(id).isTrue() : "?"))
                        .toList();

                results.add(AutomationLog.ConditionResult.builder()
                        .conditionNodeId(op.getNodeId())
                        .conditionType("operator/" + op.getLogicType()
                                + " (priority=" + op.getPriority() + ")")
                        .triggerKey("operator")
                        .passed(nr.isTrue())
                        .expectedValue(op.getLogicType() + "(" + String.join(", ", inputIds) + ")")
                        .actualValue(String.join(", ", inputResults))
                        .detail(op.getLogicType() + "([" + String.join(", ", inputResults) + "]) → "
                                + (nr.isTrue() ? "TRUE" : "FALSE"))
                        .isGateCondition(false)
                        .build());
            }
        }
        return results;
    }


    // ═════════════════════════════════════════════════════════════════════
    // REDIS / CACHE HELPERS
    // ═════════════════════════════════════════════════════════════════════

    private AutomationCache loadOrInitCache(String cacheKey, Automation automation) {
        AutomationCache cache = redisService.getAutomationCache(cacheKey);
        if (cache == null)
            cache = AutomationCache.builder()
                    .id(automation.getId())
                    .triggeredPreviously(false)
                    .previousExecutionTime(null)
                    .lastUpdate(new Date())
                    .state(AutomationState.IDLE)
                    .branchStates(new HashMap<>())
                    .build();
        return cache;
    }

    private <T> T withLock(String lockKey, long ttlSeconds, Supplier<T> function) {
        String lockValue = UUID.randomUUID().toString();
        boolean acquired = false;
        try {
            acquired = redisService.setIfAbsent(lockKey, lockValue, ttlSeconds);
            if (!acquired) {
                log.warn("Could not acquire lock: {} — returning null for fallback", lockKey);
                return null;
            }
            return function.get();
        } finally {
            if (acquired) releaseLock(lockKey, lockValue);
        }
    }

    private void releaseLock(String lockKey, String lockValue) {
        try {
            redisService.deleteIfEquals(lockKey, lockValue);
            log.debug("🔓 Lock released: {}", lockKey);
        } catch (Exception e) {
            log.error("Failed to release lock: {}", lockKey, e);
        }
    }

    private AutomationState resolveState(AutomationState stored) {
        if (stored == null) return AutomationState.IDLE;
        try {
            return AutomationState.valueOf(stored.name());
        } catch (IllegalArgumentException e) {
            return AutomationState.IDLE;
        }
    }

    private void saveLog(AutomationLog log) {
        if (log.getStatus() == AutomationLog.LogStatus.NOT_MET
                || log.getStatus() == AutomationLog.LogStatus.SKIPPED) {
            String key = "LOG_DEBOUNCE:" + log.getAutomationId();
            if (redisService.exists(key)) return;
            redisService.setWithExpiry(key, "1", 60);
        }
        logBuffer.add(log);
    }


    // ═════════════════════════════════════════════════════════════════════
    // DEVICE / MQTT HELPERS
    // ═════════════════════════════════════════════════════════════════════

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
        Map<String, Object> map = Map.of("deviceId", device.getId(),
                "reboot", true, "key", "reboot");
        messagingTemplate.convertAndSend("/topic/action." + device.getId(), map);
        sendToTopic(TOPIC_ACTION + device.getId(), map);
        try {
            new RestTemplate().getForObject(device.getAccessUrl() + "/restart", String.class);
        } catch (Exception e) {
            notificationService.sendNotification("Reboot failed: " + device.getName(), "error");
        }
        return "Rebooting device";
    }

    private void sendDirectAction(String deviceId, Map<String, Object> payload) {
        var key = payload.get("key").toString();
        var map = new HashMap<String, Object>();
        map.put(key, payload.get(key));
        map.put("key", key);
        map.put("actionType", "direct");
        messagingTemplate.convertAndSend("/topic/action." + deviceId, map);
        sendToTopic(TOPIC_ACTION + deviceId, map);
    }

    private String handleWLED(String deviceId, Map<String, Object> payload, String user) {
        var device = deviceRepository.findById(deviceId).orElse(null);
        if (device == null) return "Not found";
        try {
            return new Wled(mqttOutboundChannel, device).handleAction(payload);
        } catch (Exception e) {
            log.error("WLED error: {}", e.getMessage());
            return "Error";
        }
    }

    private Object parseData(String data) {
        if (data == null) return null;
        if ("true".equalsIgnoreCase(data)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(data)) return Boolean.FALSE;
        try {
            return data.contains(".") ? Double.parseDouble(data) : Integer.parseInt(data);
        } catch (NumberFormatException ignored) {
            return data;
        }
    }


    // ═════════════════════════════════════════════════════════════════════
    // HUMAN-READABLE MESSAGE HELPERS
    // ═════════════════════════════════════════════════════════════════════

    private String describeBranch(GateBranch branch) {
        return automationEngine.describeCondition(branch.gateCondition());
    }

    String actionSummary(List<Automation.Action> actions) {
        if (actions == null || actions.isEmpty()) return "no actions";
        return actions.stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsEnabled()))
                .map(a -> automationEngine.resolveDeviceLabel(a.getDeviceId(), a.getName())
                        + " " + a.getKey() + "=" + a.getData())
                .collect(Collectors.joining(", "));
    }


    // ═════════════════════════════════════════════════════════════════════
    // VERSION / COPY / DELETE
    // ═════════════════════════════════════════════════════════════════════

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
            log.error("Rollback failed for automation {}: {}", automationId, e.getMessage(), e);
            notificationService.sendNotification("Rollback failed: " + e.getMessage(), "error");
            return "error: " + e.getMessage();
        }
    }

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
                .isEnabled(false)
                .isActive(false)
                .updateDate(new Date())
                .build();

        Automation saved = automationRepository.save(copy);

        AutomationDetail detailCopy = automationDetailRepository.findById(id)
                .map(d -> {
                    AutomationDetail dc = new AutomationDetail();
                    dc.setId(saved.getId());
                    dc.setNodes(d.getNodes());
                    dc.setEdges(d.getEdges());
                    dc.setViewport(d.getViewport());
                    dc.setUpdateDate(new Date());
                    return dc;
                })
                .orElseGet(() -> {
                    AutomationDetail dc = new AutomationDetail();
                    dc.setId(saved.getId());
                    dc.setUpdateDate(new Date());
                    return dc;
                });
        automationDetailRepository.save(detailCopy);

        refreshCacheForAutomation(saved);

        log.info("📋 Automation '{}' copied to '{}' by {}",
                original.getName(), saved.getName(), user);
        notificationService.sendNotification("'" + original.getName() + "' copied", "success");

        return Map.of(
                "status", "success",
                "newId", saved.getId(),
                "newName", saved.getName()
        );
    }

    public Map<String, String> deleteAutomation(String id, String user) {
        return null;
    }
}