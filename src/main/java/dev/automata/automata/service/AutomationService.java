package dev.automata.automata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.automata.automata.automation.AutomationValidationService;
import dev.automata.automata.automation.PeriodicCheckEvent;
import dev.automata.automata.dto.*;
import dev.automata.automata.model.*;
import dev.automata.automata.modules.Wled;
import dev.automata.automata.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoUnit;
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

    // ── NEW: event bus injected so any method can publish without knowing listeners ──
    private final ApplicationEventPublisher eventPublisher;

    @Value("${app.location.lat}")
    private String LOCATION_LAT;
    @Value("${app.location.long}")
    private String LOCATION_LONG;
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


    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC API  (thin — just validates input and publishes an event)
    // ─────────────────────────────────────────────────────────────────────────

    public List<Automation> findAll() {
        return automationRepository.findAll();
    }

    public Automation create(Automation action) {
        return automationRepository.save(action);
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
            messagingTemplate.convertAndSend("/topic/data",
                    Map.of("deviceId", deviceId, "ack", payload));
        }
        return "success";
    }

    public String handleAction(String deviceId, Map<String, Object> payload, String deviceType, String user) {
        System.err.println("Received action");
        System.err.println("Device Type: " + deviceType);
        System.err.println("Payload: " + payload);
        System.err.println("User: " + user);

        if ("WLED".equals(deviceType)) {
            var result = handleWLED(deviceId, payload, user);
//            notifyBasedOnResult(result);
            return "success";
        }

        if ("System".equals(deviceType)) {
            var key = payload.get("key").toString();
            var data = payload.get(key).toString();
            if (payload.get("key").equals("alert")) {
                notificationService.sendNotification("", data);
            }
            if (payload.get("key").equals("app_notify")) {
                notificationService.sendNotify("Automation", data, "low");
            }
//            return "success";
        }

        if ("reboot".equals(payload.get("key"))) {
            var device = mainService.getDevice(deviceId);
            return rebootDevice(device);
        }

        if (payload.containsKey("automation")) {
            var id = payload.get(payload.get("key").toString()).toString();
            automationRepository.findById(id).ifPresent((automation) -> {
                executeAutomationImmediate(automation, new HashMap<>(), user);
            });
            return "success";
        }

        if (payload.containsValue("master")) {
            var id = payload.get("deviceId").toString();
            var device = deviceRepository.findById(id);
            var key = payload.get("key").toString();
            var value = Integer.parseInt(payload.get("value").toString());
            var screen = payload.get("screen").toString();
            var req = new HashMap<String, Object>();
            req.put("key", key);
            req.put(key, value);
            req.put("direct", true);
            req.put("deviceId", id);
            //                System.out.println("Master action sent " + req);
            //                System.out.println("Device type " + device.get().getType());
            device.ifPresent(device1 -> handleAction(id, req, device1.getType(), user));
        }

        if (payload.containsKey("direct")) {
            var check = payload.get("direct").toString();
            if (Boolean.parseBoolean(check)) {
                sendDirectAction(deviceId, payload);
                return "No saved action found but sent directly";
            }
        }

        var automations = automationRepository.findByTrigger_DeviceId(deviceId);

        automations.forEach(a -> {
//            System.err.println(a.getName());
            checkAndExecuteSingleAutomation(a, payload, user);
        });

//        notificationService.sendNotification("Action applied", "success");
        return "Action successfully sent!";
    }

    public String rebootAllDevices() {
        var devices = deviceRepository.findAll();
        notificationService.sendNotification("Rebooting All Devices", "success");
        devices.forEach(this::rebootDevice);
        notificationService.sendNotification("Reboot Complete", "success");
        return "success";
    }

    public Object sendConditionToDevice(String deviceId) {
        var payload = new HashMap<String, Object>();
        var keyJoiner = new StringJoiner(",");
        for (Automation automation : automationRepository.findByTrigger_DeviceId(deviceId)) {
            var conditions = automation.getConditions();
            if (conditions == null || conditions.isEmpty()) continue;
            var condition = conditions.get(0);
            String pVal = automation.getTrigger().getKey();
            pVal += Boolean.TRUE.equals(condition.getIsExact())
                    ? "=" + condition.getValue()
                    : ">" + condition.getAbove() + ",<" + condition.getBelow();
            payload.put(automation.getId(), pVal);
            keyJoiner.add(automation.getId());
        }
        payload.put("keys", keyJoiner.toString());
        messagingTemplate.convertAndSend("/topic/action." + deviceId, payload);
        sendToTopic(TOPIC_ACTION + deviceId, payload);
        return payload;
    }


    // ─────────────────────────────────────────────────────────────────────────
    // EVENT LISTENERS  (each handles exactly one concern, runs @Async)
    // ─────────────────────────────────────────────────────────────────────────


    /**
     * Handles a single periodic check for one automation.
     * Published by the scheduler below — one event per automation keeps each
     * check isolated and independently retryable.
     */
    @Async
    @EventListener
    public void onPeriodicCheck(PeriodicCheckEvent event) {
        checkAndExecuteSingleAutomation(
                event.getAutomation(),
                event.getRecentData(),
                "system");
    }

    /**
     * LiveEvent from MQTT/WebSocket — just updates the Redis cache.
     */
    @EventListener
    public void onCustomEvent(LiveEvent event) {
        var payload = event.getPayload();
        redisService.setRecentDeviceData(payload.get("device_id").toString(), payload);
    }


    // ─────────────────────────────────────────────────────────────────────────
    // SCHEDULED JOBS  (now just publish events — no blocking .join())
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * BEFORE: collected all futures and blocked with CompletableFuture.allOf().join().
     * AFTER:  publishes one PeriodicCheckEvent per automation and returns immediately.
     * Each event is handled @Async, so automations run in parallel without
     * the scheduler thread ever blocking.
     */
    @Scheduled(fixedRate = 12_000)
    public void triggerPeriodicAutomations() {
        // Check the database (or cache) before running
        if (!featureService.isFeatureEnabled("PERIODIC_AUTOMATION_SERVICE")) {
            // Log it, throw a custom exception, or just return quietly
            log.error("Automation service is currently disabled.");
            return;
        }
        automationRepository.findEnabledForExecution().forEach(a -> {
            AutomationCache cached = redisService.getAutomationCache(
                    a.getTrigger().getDeviceId() + ":" + a.getId());
            Automation toRun = (cached != null && cached.getAutomation() != null)
                    ? cached.getAutomation() : a;
            Map<String, Object> recentData =
                    redisService.getRecentDeviceData(a.getTrigger().getDeviceId());
//            log.info("Device data for: {} | {}", a.getName(), recentData);
            eventPublisher.publishEvent(new PeriodicCheckEvent(this, toRun, recentData));
        });
    }

    @Scheduled(fixedRate = 1000 * 60 * 5)
    public void updateRedisStorage() {
        Date fiveMinutesAgo = new Date(System.currentTimeMillis() - 5 * 60 * 1000);
        automationRepository.findAll().forEach(a -> {
            String id = a.getTrigger().getDeviceId() + ":" + a.getId();
            AutomationCache existing = redisService.getAutomationCache(id);
            if (existing != null && existing.getLastUpdate() != null
                    && existing.getLastUpdate().after(fiveMinutesAgo)) {
                return;
            }
            redisService.setAutomationCache(id, AutomationCache.builder()
                    .id(a.getId()).automation(a)
                    .triggerDeviceType(a.getTriggerDeviceType())
                    .enabled(a.getIsEnabled())
                    .state(AutomationState.IDLE)
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


    // ─────────────────────────────────────────────────────────────────────────
    // SAVE AUTOMATION
    // ─────────────────────────────────────────────────────────────────────────

    public String saveAutomationDetailWithValidation(AutomationDetail detail) {
        log.info("Validating automation detail...");
        List<String> errors = validationService.validate(detail);
        if (!errors.isEmpty()) {
            log.error("Automation validation failed: {}", errors);
            notificationService.sendNotification(
                    "Validation failed: " + String.join(", ", errors), "error");
            return "validation_failed: " + String.join("; ", errors);
        }
        return saveAutomationDetailInternal(detail);
    }

    public String saveAutomationDetail(AutomationDetail detail) {
        return saveAutomationDetailInternal(detail);
    }

    private String saveAutomationDetailInternal(AutomationDetail detail) {
        log.info("Saving automation detail: {}", detail);

        var automationBuilder = Automation.builder()
                .isEnabled(true).updateDate(new Date()).isActive(false);

        if (detail.getId() != null && !detail.getId().isEmpty()) {
            automationBuilder.id(detail.getId());
        }

        detail.getNodes().stream()
                .filter(n -> n.getData().getTriggerData() != null)
                .findFirst()
                .ifPresent(triggerNode -> {
                    var tData = triggerNode.getData().getTriggerData();
                    automationBuilder.trigger(new Automation.Trigger(
                            tData.getDeviceId(), tData.getType(), tData.getValue(), tData.getKey(),
                            tData.getKeys().stream().map(t -> t.getKey()).toList(),
                            tData.getName(), tData.getPriority(), tData.getNodeId()));
                    automationBuilder.name(tData.getName());
                });

        automationBuilder.actions(
                detail.getNodes().stream()
                        .filter(n -> n.getData().getActionData() != null)
                        .map(n -> {
                            var a = n.getData().getActionData();
                            return new Automation.Action(
                                    a.getKey(), a.getDeviceId(), a.getData(), a.getName(),
                                    a.getIsEnabled(), a.getRevert(), a.getConditionGroup(),
                                    a.getOrder(), a.getDelaySeconds(), a.getPreviousNodeRef(), a.getNodeId());
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
                                c.getDurationMinutes(), c.isEnabled(), c.getPreviousNodeRef()))
                        .collect(Collectors.toList()));

        automationBuilder.operators(
                detail.getNodes().stream()
                        .map(n -> n.getData().getOperators())
                        .filter(Objects::nonNull)
                        .map(c -> new Automation.Operator(c.getType(), c.getLogicType(),
                                c.getPreviousNodeRef(), c.getNodeId()))
                        .collect(Collectors.toList()));

        var automation = automationBuilder.build();
        automation.setTriggerDeviceType(
                mainService.getDevice(automation.getTrigger().getDeviceId()).getType());

        var saved = automationRepository.save(automation);
        detail.setId(saved.getId());
        detail.setUpdateDate(new Date());
        automationDetailRepository.save(detail);

        notificationService.sendNotification("Automation saved successfully", "success");
        refreshCacheForAutomation(saved);
        return "success";
    }


    // ─────────────────────────────────────────────────────────────────────────
    // CACHE REFRESH
    // ─────────────────────────────────────────────────────────────────────────

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
                    .triggerDeviceId(automation.getTrigger().getDeviceId())
                    .isActive(existing != null && Boolean.TRUE.equals(existing.getIsActive()))
                    .triggeredPreviously(existing != null && existing.isTriggeredPreviously())
                    .previousExecutionTime(existing != null ? existing.getPreviousExecutionTime() : null)
                    .lastUpdate(new Date())
                    .build());
            log.info("🔄 Cache refreshed: {}", automation.getName());
        } catch (Exception e) {
            log.warn("⚠️ Cache refresh failed for '{}': {}", automation.getName(), e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CORE EXECUTION  (unchanged logic; callers are now event listeners only)
    // ─────────────────────────────────────────────────────────────────────────

    public void checkAndExecuteSingleAutomation(Automation automation,
                                                Map<String, Object> data,
                                                String user) {
        Date now = new Date();
        String deviceId = automation.getTrigger().getDeviceId();
        String cacheKey = deviceId + ":" + automation.getId();

        String executionLockKey = "EXEC_LOCK:" + automation.getId();
        boolean lockAcquired = redisService.setIfAbsent(executionLockKey, "locked", LOCK_TTL_SECONDS);
        var automationLog = AutomationLog.builder()
                .automationId(automation.getId())
                .user(user)
                .automationName(automation.getName())
                .payload(data != null ? data : Map.of())
                .triggerDeviceId(deviceId).timestamp(now);
        if (!lockAcquired) {
            log.debug("⏭️ Skipping {} — lock held", automation.getName());
            saveLog(automationLog
                    .status(AutomationLog.LogStatus.SKIPPED)
                    .reason("Skipped — execution lock held by concurrent caller")
                    .build());
            return;
        }
        String SNOOZE_KEY = "SNOOZE:";
        String DISABLE_KEY = "TIMED_DISABLE:";
// ── Snooze / timed-disable gate ──────────────────────────────────────────
        if (redisService.exists(SNOOZE_KEY + automation.getId())) {
            long remaining = redisService.getTTL(SNOOZE_KEY + automation.getId());
            log.debug("⏸️ Skipping '{}' — snoozed ({} min remaining)",
                    automation.getName(), remaining / 60);
            saveLog(automationLog
                    .status(AutomationLog.LogStatus.SKIPPED)
                    .snoozeState("SKIPPED")
                    .reason("Snoozed — " + remaining / 60 + " min remaining")
                    .build());
            return;
        }

        if (redisService.exists(DISABLE_KEY + automation.getId())) {
            long remaining = redisService.getTTL(DISABLE_KEY + automation.getId());
            log.debug("🚫 Skipping '{}' — timed disabled ({} min remaining)",
                    automation.getName(), remaining / 60);
            saveLog(automationLog
                    .status(AutomationLog.LogStatus.SKIPPED)
                    .reason("Timed-disabled — " + remaining / 60 + " min remaining")
                    .build());
            return;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            if (data != null) payload.putAll(data);
            else payload.putAll(mainService.getLastData(deviceId));

            AutomationCache cache = withLock("LOCK:CACHE:" + cacheKey, LOCK_TTL_SECONDS, () -> {
                AutomationCache c = redisService.getAutomationCache(cacheKey);
                if (c == null) c = AutomationCache.builder()
                        .id(automation.getId()).triggeredPreviously(false)
                        .previousExecutionTime(null).lastUpdate(new Date())
                        .state(AutomationState.IDLE).build();
                return c;
            });
            if (cache == null) {
                cache = redisService.getAutomationCache(cacheKey);
                if (cache == null) cache = AutomationCache.builder()
                        .id(automation.getId()).triggeredPreviously(false)
                        .previousExecutionTime(null).lastUpdate(new Date())
                        .state(AutomationState.IDLE).build();
            }

            AutomationState currentState = resolveState(cache.getState());
            boolean wasActive = (currentState == AutomationState.ACTIVE
                    || currentState == AutomationState.HOLDING);

//            boolean conditionNow = isTriggered(automation, payload, wasActive, conditionResults);
            ExecutionContext ctx = new ExecutionContext();

            NodeResult result = evaluate(
                    automation,
                    payload,
                    ctx,
                    wasActive
            );

            boolean conditionNow = result.isTrue();
            List<AutomationLog.ConditionResult> conditionResults =
                    buildConditionResults(automation, ctx, payload);

            automationLog
                    .conditionResults(conditionResults).payload(payload)
                    .triggerDeviceId(deviceId).timestamp(now);
            List<Automation.Action> positiveActions = resolveActions(automation, ctx, "positive");
            List<Automation.Action> negativeActions = resolveActions(automation, ctx, "negative");
//            log.info("[{}] Positive actions {}", automation.getName(), positiveActions);
//            log.info("[{}] Negative actions {}", automation.getName(), negativeActions);

            Set<String> operatorIds = automation.getOperators() == null ? Set.of() :
                    automation.getOperators().stream()
                    .map(Automation.Operator::getNodeId)
                    .collect(Collectors.toSet());

            List<Automation.Condition> activeDurationConditions = automation.getConditions().stream()
                    .filter(c -> c.isEnabled() && c.getDurationMinutes() > 0)
                    .filter(c -> {
                        // Only gate conditions own duration — trigger conditions feed the operator
                        return c.getPreviousNodeRef() != null &&
                                c.getPreviousNodeRef().stream()
                                        .anyMatch(ref -> operatorIds.contains(ref.getNodeId()));
                    })
                    .filter(c -> {
                        NodeResult nr = ctx.get(c.getNodeId());
                        return nr != null && nr.isTrue();
                    })
                    .toList();
            // ── Check if ANY active duration condition still has its timer running ────
            // Per-condition key: RUNNING:{automationId}:{conditionNodeId}
            boolean durationActive = activeDurationConditions.stream()
                    .anyMatch(c -> redisService.exists(
                            "RUNNING:" + automation.getId() + ":" + c.getNodeId()));

            boolean hasDuration = !activeDurationConditions.isEmpty();
            try {
                if (currentState == AutomationState.IDLE && conditionNow) {
                    // ── Interval cooldown check (per condition) ───────────────────────────
                    for (Automation.Condition c : activeDurationConditions) {
                        if (c.getIntervalMinutes() > 0 && cache.getPreviousExecutionTime() != null) {
                            long secondsSinceLast =
                                    (now.getTime() - cache.getPreviousExecutionTime().getTime()) / 1000;
                            long intervalSeconds = c.getIntervalMinutes() * 60L;
                            if (secondsSinceLast < intervalSeconds) {
                                log.debug("⏳ [{}] Condition '{}' in interval cooldown — {}s remaining",
                                        automation.getName(), c.getNodeId(),
                                        intervalSeconds - secondsSinceLast);
                                automationLog.status(AutomationLog.LogStatus.SKIPPED)
                                        .reason("Interval cooldown on " + c.getNodeId() + " — "
                                                + (intervalSeconds - secondsSinceLast) + "s remaining");
                                saveLog(automationLog.build());
                                return;
                            }
                        }
                    }


                    final AutomationCache finalCache = cache;

                    executeWithTimeout(positiveActions, payload, user, automation.getName())
                            .thenAccept(success -> {
                                if (!Boolean.TRUE.equals(success)) {
                                    log.warn("⚠️ Positive execution failed for '{}' — cache NOT advanced",
                                            automation.getName());
                                    return;
                                }

                                finalCache.setTriggeredPreviously(true);
                                finalCache.setPreviousExecutionTime(now);
                                finalCache.setLastUpdate(new Date());
                                notificationService.sendNotification("🚀 Triggered: " + automation.getName(), "success");
                                log.info("🚀 [{}] Triggered", automation.getName());
                                if (hasDuration) {
                                    finalCache.setState(AutomationState.HOLDING);

                                    // Set a timer key per condition, each with its own TTL
                                    activeDurationConditions.forEach(c -> {
                                        String runKey = "RUNNING:" + automation.getId() + ":" + c.getNodeId();
                                        redisService.setWithExpiry(runKey, "active",
                                                c.getDurationMinutes() * 60L);
                                        log.info("⏱️ [{}] Duration timer set for condition '{}' — {} min",
                                                automation.getName(), c.getNodeId(), c.getDurationMinutes());
                                    });

                                } else {
                                    finalCache.setState(AutomationState.ACTIVE);
                                    log.info("✅ [{}] Entered ACTIVE (no duration)",
                                            automation.getName());
                                }

                                redisService.setAutomationCache(cacheKey, finalCache);
                            });

                    automationLog.status(AutomationLog.LogStatus.TRIGGERED)
                            .reason("Condition TRUE → positive actions executed");

                } else if (currentState == AutomationState.ACTIVE
                        || currentState == AutomationState.HOLDING) {

                    boolean shouldRevert = false;
                    String reason;

                    if (!conditionNow) {
                        shouldRevert = true;
                        reason = "Condition turned FALSE";

                    } else if (currentState == AutomationState.HOLDING) {
                        // Collect conditions that WERE running (had a timer) but timer has now expired
                        // A condition's duration expired = it was set (we know because state is HOLDING)
                        // but the key is gone now.
                        // We check all duration conditions — if ALL timers expired → revert.
                        // If ANY still running → stay HOLDING.
                        List<Automation.Condition> durationConditions = automation.getConditions().stream()
                                .filter(c -> c.isEnabled() && c.getDurationMinutes() > 0)
                                .toList();

                        boolean anyTimerStillRunning = durationConditions.stream()
                                .anyMatch(c -> redisService.exists(
                                        "RUNNING:" + automation.getId() + ":" + c.getNodeId()));

                        if (!anyTimerStillRunning && !durationConditions.isEmpty()) {
                            shouldRevert = true;
                            reason = "All duration timers expired";
                        } else {
                            reason = "";
                        }
                    } else {
                        reason = "";
                    }

                    if (shouldRevert) {


                        final AutomationCache finalCache = cache;
                        final String revertReason = reason;

                        executeWithTimeout(negativeActions, payload, user, automation.getName())
                                .thenAccept(success -> {
                                    if (!Boolean.TRUE.equals(success)) return;
                                    notificationService.sendNotification("⏹️ Reverting automation: " + automation.getName(), "success");
                                    log.info("⏹️ [{}] Reverting automation: ({})",
                                            automation.getName(), reason);
                                    // Clear all per-condition duration keys
                                    automation.getConditions().stream()
                                            .filter(c -> c.getDurationMinutes() > 0)
                                            .forEach(c -> redisService.delete(
                                                    "RUNNING:" + automation.getId() + ":" + c.getNodeId()));

                                    finalCache.setState(AutomationState.IDLE);
                                    finalCache.setTriggeredPreviously(false);
                                    finalCache.setLastUpdate(new Date());
                                    redisService.setAutomationCache(cacheKey, finalCache);

                                    log.info("🔁 [{}] Reset to IDLE — {}", automation.getName(), revertReason);
                                });
                        automationLog.status(AutomationLog.LogStatus.RESTORED)
                                .endTimestamp(new Date())
                                .reason(reason + " → negative actions executed");
                    } else {
                        automationLog.status(AutomationLog.LogStatus.SKIPPED)
                                .reason("Still ACTIVE/HOLDING — no revert needed");
                    }
                }

                // ❌ NOT MET (IDLE + condition false — normal quiet path)
                else if (currentState == AutomationState.IDLE) {
                    automationLog.status(AutomationLog.LogStatus.NOT_MET)
                            .reason("Condition not satisfied");
                }

                // ⏭️ DEFAULT SKIP (should not normally be reached with typed enum)
                else {
                    log.debug("⏭️ No state change for '{}' (state={}, conditionNow={})",
                            automation.getName(), currentState, conditionNow);
                    automationLog.status(AutomationLog.LogStatus.SKIPPED)
                            .reason("No state change (state=" + currentState + ")");
                }
            } catch (Exception e) {
                notificationService.sendNotification("❌ Error in automation: " + automation.getName(), "success");
                log.error("❌ Error in automation {}: {}", automation.getName(), e.getMessage(), e);
                automationLog.status(AutomationLog.LogStatus.ERROR)
                        .reason("Execution failed: " + e.getMessage());
            }

            saveLog(automationLog.build());

        } finally {
            releaseLock(executionLockKey, "locked");
        }
    }

    // ─── Add this method ───────────────────────────────────────────────────────

    private List<AutomationLog.ConditionResult> buildConditionResults(
            Automation automation,
            ExecutionContext ctx,
            Map<String, Object> payload) {

        List<AutomationLog.ConditionResult> results = new ArrayList<>();

        // ── Condition nodes ──────────────────────────────────────────────────
        for (Automation.Condition c : automation.getConditions()) {
            NodeResult nr = ctx.get(c.getNodeId());
            if (nr == null) continue;

            AutomationLog.ConditionResult.ConditionResultBuilder builder =
                    AutomationLog.ConditionResult.builder()
                            .conditionNodeId(c.getNodeId())
                            .conditionType(c.getCondition())
                            .triggerKey(c.getTriggerKey())
                            .passed(nr.isTrue());

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
                double buffer = 5.0;

                String expected = switch (c.getCondition()) {
                    case "above" -> "> " + c.getValue();
                    case "below" -> "< " + c.getValue();
                    case "range" -> c.getAbove() + " < x < " + c.getBelow();
                    default -> c.getValue();
                };

                String detail = switch (c.getCondition()) {
                    case "above" -> {
                        double t = Double.parseDouble(c.getValue());
                        yield raw + " > " + t + " → " + (nr.isTrue() ? "PASS" : "FAIL");
                    }
                    case "below" -> {
                        double t = Double.parseDouble(c.getValue());
                        yield raw + " < " + t + " → " + (nr.isTrue() ? "PASS" : "FAIL");
                    }
                    case "range" -> raw + " in (" + c.getAbove() + ", " + c.getBelow() + ")"
                            + " → " + (nr.isTrue() ? "PASS" : "FAIL");
                    default -> raw + " == " + c.getValue()
                            + " → " + (nr.isTrue() ? "PASS" : "FAIL");
                };

                builder.actualValue(raw)
                        .expectedValue(expected)
                        .detail(detail);

            } else {
                // key missing from payload
                builder.actualValue("missing")
                        .expectedValue(c.getValue())
                        .detail("Key '" + c.getTriggerKey() + "' not present in payload");
            }

            // Gate context — note if this condition is downstream of an operator
            boolean isGate = c.getPreviousNodeRef() != null &&
                    c.getPreviousNodeRef().stream().anyMatch(ref ->
                            automation.getOperators().stream()
                                    .anyMatch(op -> op.getNodeId().equals(ref.getNodeId())));
            builder.isGateCondition(isGate);

            results.add(builder.build());
        }

        // ── Operator nodes ───────────────────────────────────────────────────
        if (automation.getOperators() != null) {
            for (Automation.Operator op : automation.getOperators()) {
                NodeResult nr = ctx.get(op.getNodeId());
                if (nr == null) continue;

                List<String> inputIds = op.getPreviousNodeRef().stream()
                        .map(ref -> ref.getNodeId())
                        .toList();

                List<String> inputResults = inputIds.stream().map(id -> {
                    NodeResult input = ctx.get(id);
                    return id + "=" + (input != null ? input.isTrue() : "?");
                }).toList();

                results.add(AutomationLog.ConditionResult.builder()
                        .conditionNodeId(op.getNodeId())
                        .conditionType("operator/" + op.getLogicType())
                        .triggerKey("operator")
                        .passed(nr.isTrue())
                        .expectedValue(op.getLogicType() + "(" + String.join(", ", inputIds) + ")")
                        .actualValue(String.join(", ", inputResults))
                        .detail(op.getLogicType() + "([" + String.join(", ", inputResults) + "]) → "
                                + (nr.isTrue() ? "TRUE" : "FALSE")
                                + (nr.getContributors().isEmpty() ? ""
                                : " | contributors: " + nr.getContributors()))
                        .isGateCondition(false)
                        .build());
            }
        }

        return results;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // IMMEDIATE / OVERRIDE EXECUTION
    // ─────────────────────────────────────────────────────────────────────────

    private void executeAutomationImmediate(Automation automation,
                                            Map<String, Object> payload,
                                            String user) {
        if (!automation.getIsEnabled()) return;

        var automationLog = AutomationLog.builder()
                .automationId(automation.getId()).automationName(automation.getName())
                .conditionResults(new ArrayList<>()).operatorLogic("")
                .payload(payload).triggerType(automation.getTrigger().getType())
                .triggerDeviceId(automation.getTrigger().getDeviceId())
                .timestamp(new Date());

        executeWithTimeout(automation.getActions(), payload, user, automation.getName())
                .thenAccept(success -> {
                    if (Boolean.TRUE.equals(success)) {
                        automationLog
                                .status(AutomationLog.LogStatus.USER_OVERRIDE)
                                .reason("Automation triggered manually by user: " + user);
                        log.info("✅ User override completed: {}", automation.getName());
                    } else {
                        automationLog
                                .status(AutomationLog.LogStatus.ERROR)
                                .reason("User override FAILED for user: " + user);
                        log.error("❌ User override failed: {}", automation.getName());
                    }
                    saveLog(automationLog.build());
                })
                .exceptionally(ex -> {
                    automationLog
                            .status(AutomationLog.LogStatus.ERROR)
                            .reason("User override FAILED for user: " + user + " — " + ex.getMessage());
                    saveLog(automationLog.build());
                    log.error("❌ User override exception: {}", automation.getName(), ex);
                    return null;
                });
    }


    public NodeResult evaluate(Automation automation,
                               Map<String, Object> payload,
                               ExecutionContext ctx,
                               boolean wasActive) {

        List<Automation.Condition> conditions = automation.getConditions() == null
                ? List.of() : automation.getConditions();
        List<Automation.Operator> operators = automation.getOperators() == null
                ? List.of() : automation.getOperators();

        // ── Classify ─────────────────────────────────────────────────────────────
        // Gate condition: previousNodeRef points to an operator node.
        // Trigger condition: everything else — feeds into operators or IS the root.

        Set<String> operatorIds = operators.stream()
                .map(Automation.Operator::getNodeId)
                .collect(Collectors.toSet());

        List<Automation.Condition> triggerConditions = new ArrayList<>();
        List<Automation.Condition> gateConditions = new ArrayList<>();

        for (Automation.Condition c : conditions) {
            if (!c.isEnabled()) continue;
            boolean isGate = c.getPreviousNodeRef() != null &&
                    c.getPreviousNodeRef().stream()
                            .anyMatch(ref -> operatorIds.contains(ref.getNodeId()));
            (isGate ? gateConditions : triggerConditions).add(c);
        }

        // ── Phase 1: evaluate trigger conditions ─────────────────────────────────
        for (Automation.Condition c : triggerConditions) {
            boolean result = evaluateCondition(automation, c, payload, wasActive);
            NodeResult nr = new NodeResult(c.getNodeId(), result);
            nr.getContributors().add(c.getNodeId());
            ctx.put(nr);
        }

        // ── Phase 1b: operators (skip entirely if none) ───────────────────────────
        for (Automation.Operator op : operators) {
            List<NodeResult> inputs = op.getPreviousNodeRef().stream()
                    .map(ref -> ctx.get(ref.getNodeId()))
                    .filter(Objects::nonNull)
                    .toList();

            boolean result;
            Set<String> contributors = new HashSet<>();

            if (inputs.isEmpty()) {
                // Operator with no resolved inputs — treat as false, log it
                log.warn("⚠️ Operator '{}' has no resolved inputs — defaulting to false", op.getNodeId());
                result = false;
            } else if ("OR".equalsIgnoreCase(op.getLogicType())) {
                result = inputs.stream().anyMatch(NodeResult::isTrue);
                inputs.stream().filter(NodeResult::isTrue)
                        .forEach(n -> contributors.addAll(n.getContributors()));
            } else { // AND / default
                result = inputs.stream().allMatch(NodeResult::isTrue);
                inputs.forEach(n -> contributors.addAll(n.getContributors()));
            }

            NodeResult opResult = new NodeResult(op.getNodeId(), result);
            opResult.getContributors().addAll(contributors);
            ctx.put(opResult);
        }

        // ── Phase 2: gate conditions ──────────────────────────────────────────────
        for (Automation.Condition c : gateConditions) {
            boolean parentTrue = c.getPreviousNodeRef().stream()
                    .map(ref -> ctx.get(ref.getNodeId()))
                    .filter(Objects::nonNull)
                    .anyMatch(NodeResult::isTrue);

            boolean result = parentTrue && evaluateCondition(automation, c, payload, wasActive);
            NodeResult nr = new NodeResult(c.getNodeId(), result);
            if (result) nr.getContributors().add(c.getNodeId());
            ctx.put(nr);
        }

        // ── Root resolution ───────────────────────────────────────────────────────
        return findRoot(operators, triggerConditions, operatorIds, ctx);
    }

    private NodeResult findRoot(List<Automation.Operator> operators,
                                List<Automation.Condition> triggerConditions,
                                Set<String> operatorIds,
                                ExecutionContext ctx) {

        // Case A: has operators → find the topological root operator
        // (the one not referenced as input by any other operator)
        if (!operators.isEmpty()) {
            Set<String> referencedByOtherOps = operators.stream()
                    .flatMap(op -> op.getPreviousNodeRef().stream())
                    .map(NodeRef::getNodeId)
                    .filter(operatorIds::contains)
                    .collect(Collectors.toSet());

            return operators.stream()
                    .filter(op -> !referencedByOtherOps.contains(op.getNodeId()))
                    .map(op -> ctx.get(op.getNodeId()))
                    .filter(Objects::nonNull)
                    .findFirst()
                    // Fallback: all operators are cross-referenced somehow — take last
                    .orElseGet(() -> {
                        log.warn("⚠️ Could not determine root operator topologically — using last");
                        return ctx.get(operators.get(operators.size() - 1).getNodeId());
                    });
        }

        // Case B: no operators, multiple trigger conditions
        // → implicit AND across all of them, synthesize a virtual root result
        if (triggerConditions.size() > 1) {
            boolean allTrue = triggerConditions.stream()
                    .map(c -> ctx.get(c.getNodeId()))
                    .filter(Objects::nonNull)
                    .allMatch(NodeResult::isTrue);

            Set<String> contributors = triggerConditions.stream()
                    .map(c -> ctx.get(c.getNodeId()))
                    .filter(nr -> nr != null && nr.isTrue())
                    .flatMap(nr -> nr.getContributors().stream())
                    .collect(Collectors.toSet());

            // Use a synthetic node id so resolveActions() can reference it if needed
            NodeResult synthetic = new NodeResult("root:implicit_and", allTrue);
            synthetic.getContributors().addAll(contributors);
            ctx.put(synthetic);

            log.debug("🔗 Implicit AND across {} conditions → {}", triggerConditions.size(), allTrue);
            return synthetic;
        }

        // Case C: single trigger condition — it IS the root
        if (!triggerConditions.isEmpty()) {
            NodeResult nr = ctx.get(triggerConditions.getFirst().getNodeId());
            if (nr != null) return nr;
        }

        // Case D: nothing evaluated (all conditions disabled, empty automation)
        log.warn("⚠️ No evaluable nodes found — returning false root");
        return new NodeResult("root:empty", false);
    }

    private boolean evaluateCondition(Automation automation, Automation.Condition condition,
                                      Map<String, Object> payload,
                                      boolean wasActive) {

        if ("scheduled".equals(condition.getCondition())) {
            return isCurrentTimeWithDailyTracking(automation, condition);
        }

        String key = condition.getTriggerKey();
        if (key == null || !payload.containsKey(key)) return false;

        String value = payload.get(key).toString();

        if (!value.matches("-?\\d+(\\.\\d+)?")) {
            // String equality check
            return value.equals(condition.getValue());
        }

        double v = Double.parseDouble(value);
        double buffer = 5.0;

        if (Boolean.TRUE.equals(condition.getIsExact())) {
            return condition.getValue().equals(value);
        }

        return switch (condition.getCondition()) {
            case "above" -> wasActive
                    ? v > (Double.parseDouble(condition.getValue()) - buffer)
                    : v > Double.parseDouble(condition.getValue());

            case "below" -> wasActive
                    ? v < (Double.parseDouble(condition.getValue()) + buffer)
                    : v < Double.parseDouble(condition.getValue());

            case "range" -> {                                    // ← was missing
                double above = Double.parseDouble(condition.getAbove());
                double below = Double.parseDouble(condition.getBelow());
                yield wasActive
                        ? v > (above - buffer) && v < (below + buffer)
                        : v > above && v < below;
            }

            default -> false;
        };
    }

    public List<Automation.Action> resolveActions(
            Automation automation,
            ExecutionContext ctx,
            String group) {

        Set<String> trueNodes = ctx.getTrueNodes();
        Set<String> falseNodes = ctx.getFalseNodes();

        return automation.getActions().stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsEnabled()))
                .filter(a -> group.equalsIgnoreCase(a.getConditionGroup()))
                .filter(a -> {
                    if (a.getPreviousNodeRef() == null || a.getPreviousNodeRef().isEmpty()) {
                        return true; // global action, always included
                    }

                    return a.getPreviousNodeRef().stream().anyMatch(ref -> {
                        String handle = ref.getHandle();

                        // Explicit negative handle → node must be FALSE
                        if (handle != null && handle.contains("cond-negative")) {
                            return falseNodes.contains(ref.getNodeId());
                        }

                        // Explicit positive handle or operator output → node must be TRUE
                        return trueNodes.contains(ref.getNodeId());
                    });
                })
                .sorted(Comparator.comparing(a ->
                        a.getOrder() != 0 ? a.getOrder() : Integer.MAX_VALUE))
                .toList();
    }

    private boolean isCurrentTimeWithDailyTracking(Automation automation,
                                                   Automation.Condition condition) {
        ZoneId istZone = ZoneId.of("Asia/Kolkata");
        ZonedDateTime nowZdt = ZonedDateTime.now(istZone);
        LocalTime current = nowZdt.toLocalTime();

        if (condition.getDays() != null && !condition.getDays().isEmpty()) {
            String dow = nowZdt.getDayOfWeek()
                    .getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)
                    .substring(0, 3);
            dow = dow.substring(0, 1).toUpperCase() + dow.substring(1).toLowerCase();
            if (!condition.getDays().contains("Everyday") && !condition.getDays().contains(dow)) {
                return false;
            }
        }

        String scheduleType = condition.getScheduleType();
        if ("range".equals(scheduleType)) {
            LocalTime from = parseTime(condition.getFromTime());
            LocalTime to = parseTime(condition.getToTime());
            if (from == null || to == null) return false;
            return from.isBefore(to)
                    ? !current.isBefore(from) && !current.isAfter(to)
                    : !current.isBefore(from) || !current.isAfter(to);
        }
        if ("solar".equals(scheduleType)) {
            LocalTime solarTime = getSunTime(condition.getSolarType());
            if (solarTime == null) return false;
            LocalTime adjusted = solarTime.plusMinutes(condition.getOffsetMinutes());
            boolean match = Math.abs(ChronoUnit.MINUTES.between(adjusted, current)) <= 3;
            return match && checkAndSetDailyLock(automation, nowZdt);
        }
        if ("interval".equals(scheduleType)) {
            String intervalKey = "INTERVAL:" + automation.getId() + ":" + condition.getNodeId();
            // Use the per-condition run key that matches what durationMinutes sets
            String runKey = "RUNNING:" + automation.getId() + ":" + condition.getNodeId();
            if (redisService.exists(runKey)) return true;
            if (!redisService.exists(intervalKey)) {
                redisService.setWithExpiry(intervalKey, "run", condition.getIntervalMinutes() * 60L);
                return true;
            }
            return false;
        }

        LocalTime target = parseTime(condition.getTime());
        if (target == null) return false;
        if (Math.abs(ChronoUnit.MINUTES.between(target, current)) > 1) return false;

        LocalDate today = nowZdt.toLocalDate();
        String dailyKey = String.format("DAILY_FIRE:%s:%s", automation.getId(), today);
        if (redisService.exists(dailyKey)) return false;

        long secondsUntilMidnight = ChronoUnit.SECONDS.between(
                nowZdt, nowZdt.plusDays(1).truncatedTo(ChronoUnit.DAYS));
        redisService.setWithExpiry(dailyKey, "fired", secondsUntilMidnight);
        return true;
    }

    private boolean checkAndSetDailyLock(Automation automation, ZonedDateTime nowZdt) {
        LocalDate today = nowZdt.toLocalDate();
        String key = String.format("DAILY_SOLAR:%s:%s", automation.getId(), today);
        if (redisService.exists(key)) return false;
        long ttl = ChronoUnit.SECONDS.between(nowZdt,
                nowZdt.plusDays(1).truncatedTo(ChronoUnit.DAYS));
        redisService.setWithExpiry(key, "done", ttl);
        return true;
    }


    private LocalTime getSunTime(String solarType) {
        try {
            ZoneId istZone = ZoneId.of("Asia/Kolkata");
            LocalDate today = LocalDate.now(istZone);
            String cacheKey = "SUN_TIME:" + solarType + "-" + today;
            Object cached = redisService.get(cacheKey);
            if (cached != null) return LocalTime.parse(cached.toString());

            Map<String, Object> response = new RestTemplate().getForObject(
                    "https://api.sunrise-sunset.org/json?lat=" + LOCATION_LAT + "&lng=" + LOCATION_LONG + "&formatted=0",
                    Map.class);
            if (response == null || !response.containsKey("results")) return null;

            Map<String, String> results = (Map<String, String>) response.get("results");
            String timeStr = "sunrise".equalsIgnoreCase(solarType)
                    ? results.get("sunrise") : results.get("sunset");
            if (timeStr == null) return null;

            LocalTime result = ZonedDateTime.parse(timeStr)
                    .withZoneSameInstant(istZone).toLocalTime();
            redisService.setWithExpiry(cacheKey, result.toString(), 86400);
            return result;
        } catch (Exception e) {
            log.error("❌ Sun time fetch failed: {}", e.getMessage());
            return null;
        }
    }


    // ─────────────────────────────────────────────────────────────────────────
    // ACTION EXECUTION  (unchanged)
    // ─────────────────────────────────────────────────────────────────────────

    private CompletableFuture<Boolean> executeWithTimeout(List<Automation.Action> orderedActions,
                                                          Map<String, Object> payload,
                                                          String user, String automationName) {
        return executeActionsInternal(orderedActions, user, payload, automationName)
                .orTimeout(AUTOMATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    if (cause instanceof TimeoutException) {
                        log.error("⏱️ Timeout: {}", automationName);
                        notificationService.sendNotification(
                                "Automation timeout: " + automationName, "error");
                    } else {
                        log.error("❌ Async error in {}: {}", automationName,
                                cause.getMessage(), cause);
                    }
                    return false;
                });
    }


    // ─────────────────────────────────────────────────────────────────────────
    // NON-BLOCKING CHAIN BUILDER
    // Replaces the for-loop + Thread.sleep with recursive CompletableFuture
    // composition. Each action is:
    //   1. Run on automationExecutor  (non-blocking thread pool)
    //   2. If a delay follows, park via ScheduledExecutorService (no thread held)
    //   3. Then recurse to the next action
    // ─────────────────────────────────────────────────────────────────────────

    private CompletableFuture<Boolean> executeActionsInternal(
            List<Automation.Action> orderedActions,
            String user,
            Map<String, Object> value, String automationName) {

        CompletableFuture<Boolean> chain = CompletableFuture.completedFuture(true);

        for (Automation.Action action : orderedActions) {
            chain = chain.thenCompose(previousOk -> {
                // Run this action on the executor pool
                CompletableFuture<Boolean> actionFuture =
                        CompletableFuture.supplyAsync(() -> {
                            try {
                                log.info("▶️ [{}] Executing action: {} (order={})", automationName,
                                        action.getName(), action.getOrder());
                                executeSingleAction(action, user, value);
                                return true;
                            } catch (Exception e) {
                                log.error("❌ [{}] Failed action: {}", automationName, action.getName(), e);
                                return false;  // continue chain even on failure
                            }
                        }, automationExecutor);

                // After the action completes, schedule the delay (if any) without
                // holding a thread — ScheduledExecutorService parks it for free.
                int delaySec = action.getDelaySeconds();
                if (delaySec > 0) {
                    return actionFuture.thenCompose(ok -> {
                        log.info("⏳ [{}] Delay {}s after action '{}' (non-blocking)", automationName,
                                delaySec, action.getName());
                        CompletableFuture<Boolean> delayedFuture = new CompletableFuture<>();
                        delayScheduler.schedule(
                                () -> delayedFuture.complete(ok),
                                delaySec,
                                TimeUnit.SECONDS
                        );
                        return delayedFuture;
                    });
                }

                return actionFuture;
            });
        }

        return chain;
    }

    private void executeSingleAction(
            Automation.Action action,
            String user,
            Map<String, Object> value) {

        Object parsedData = parseData(action.getData());

        Map<String, Object> payload = Map.of(
                action.getKey(), parsedData,
                "key", action.getKey()
        );

        if ("alert".equals(action.getKey())) {
            notificationService.sendAlert(
                    "Alert: " + action.getData().toUpperCase(Locale.ROOT),
                    action.getData());

        } else if ("app_notify".equals(action.getKey())) {
            notificationService.sendNotify(
                    "Automation",
                    action.getData() + " and live data are " + value,
                    "low");

        } else if ("WLED".equals(mainService.getDevice(action.getDeviceId()).getType())) {
            handleWLED(action.getDeviceId(), new HashMap<>(payload), user);

        } else {
            deviceActionStateRepository.save(DeviceActionState.builder()
                    .user(user)
                    .deviceId(action.getDeviceId())
                    .timestamp(new Date())
                    .payload(payload)
                    .deviceType("sensor")
                    .build());

            messagingTemplate.convertAndSend("/topic/action." + action.getDeviceId(), payload);
            sendToTopic("action/" + action.getDeviceId(), payload);
        }
    }


    // ─────────────────────────────────────────────────────────────────────────
    // REDIS HELPERS
    // ─────────────────────────────────────────────────────────────────────────

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
            if (acquired) {
                releaseLock(lockKey, lockValue);
            }
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

    private void saveLog(AutomationLog automationLog) {
        if (automationLog.getStatus() == AutomationLog.LogStatus.NOT_MET) {
            String debounceKey = "LOG_DEBOUNCE:" + automationLog.getAutomationId();
            if (redisService.exists(debounceKey)) return;
            redisService.setWithExpiry(debounceKey, "1", 60);
        }
        automationLogRepository.save(automationLog);
    }


    // ─────────────────────────────────────────────────────────────────────────
    // DEVICE / MQTT HELPERS  (unchanged)
    // ─────────────────────────────────────────────────────────────────────────

    private void sendToTopic(String topic, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            mqttOutboundChannel.send(
                    MessageBuilder.withPayload(json)
                            .setHeader("mqtt_topic", topic).build());
        } catch (Exception e) {
            log.error("MQTT send error: {}", e.getMessage());
        }
    }

    private String rebootDevice(Device device) {
        if (device == null) return "Device not found";
        Map<String, Object> map = Map.of("deviceId", device.getId(), "reboot", true, "key", "reboot");
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
        messagingTemplate.convertAndSend("/topic/action." + deviceId, map);
        sendToTopic(TOPIC_ACTION + deviceId, map);
        notificationService.sendNotification("Action sent to device", "success");
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

    // ─────────────────────────────────────────────────────────────────────────
    // MISC HELPERS  (unchanged)
    // ─────────────────────────────────────────────────────────────────────────

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

    private LocalTime parseTime(String timeText) {
        if (timeText == null || timeText.isBlank()) return null;
        try {
            return LocalTime.parse(timeText.trim(), DateTimeFormatter.ofPattern("HH:mm:ss"));
        } catch (Exception e1) {
            try {
                return LocalTime.parse(timeText.trim(),
                        new DateTimeFormatterBuilder().parseCaseInsensitive()
                                .appendPattern("hh:mm:ss a").toFormatter(Locale.ENGLISH));
            } catch (Exception e2) {
                log.warn("⚠️ Unable to parse time: '{}'", timeText);
                return null;
            }
        }
    }

}