package dev.automata.automata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.automata.automata.automation.AutomationValidationService;
import dev.automata.automata.automation.PeriodicCheckEvent;
import dev.automata.automata.dto.AutomationCache;
import dev.automata.automata.dto.AutomationState;
import dev.automata.automata.dto.LiveEvent;
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

    // ── NEW: event bus injected so any method can publish without knowing listeners ──
    private final ApplicationEventPublisher eventPublisher;

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
            if (device.isPresent()) {
//                System.out.println("Master action sent " + req);
//                System.out.println("Device type " + device.get().getType());
                handleAction(id, req, device.get().getType(), user);
            }
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
        automationRepository.findEnabledForExecution().forEach(a -> {
            AutomationCache cached = redisService.getAutomationCache(
                    a.getTrigger().getDeviceId() + ":" + a.getId());
            Automation toRun = (cached != null && cached.getAutomation() != null)
                    ? cached.getAutomation() : a;
            Map<String, Object> recentData =
                    redisService.getRecentDeviceData(a.getTrigger().getDeviceId());

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
        if (!lockAcquired) {
            log.debug("⏭️ Skipping {} — lock held", automation.getName());
            saveLog(AutomationLog.builder()
                    .automationId(automation.getId()).automationName(automation.getName())
                    .conditionResults(new ArrayList<>())
                    .payload(data != null ? data : Map.of())
                    .triggerDeviceId(deviceId).timestamp(now)
                    .status(AutomationLog.LogStatus.SKIPPED)
                    .reason("Skipped — execution lock held by concurrent caller")
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

            List<AutomationLog.ConditionResult> conditionResults = new ArrayList<>();
            boolean conditionNow = isTriggered(automation, payload, wasActive, conditionResults);

            Automation.Condition condition = (automation.getConditions() != null
                    && !automation.getConditions().isEmpty())
                    ? automation.getConditions().get(0) : null;

            boolean hasDuration = condition != null && condition.getDurationMinutes() > 0;
            String runKey = "RUNNING:" + automation.getId();
            boolean durationActive = redisService.exists(runKey);

            var automationLog = AutomationLog.builder()
                    .automationId(automation.getId()).automationName(automation.getName())
                    .conditionResults(conditionResults).payload(payload)
                    .triggerDeviceId(deviceId).timestamp(now);

            try {
                if (currentState == AutomationState.IDLE && conditionNow) {
                    if (hasDuration && condition.getIntervalMinutes() > 0
                            && cache.getPreviousExecutionTime() != null) {
                        long secondsSinceLast =
                                (now.getTime() - cache.getPreviousExecutionTime().getTime()) / 1000;
                        long intervalSeconds = condition.getIntervalMinutes() * 60L;
                        if (secondsSinceLast < intervalSeconds) {
                            log.debug("⏳ Automation '{}' in interval cooldown — {}s remaining",
                                    automation.getName(), intervalSeconds - secondsSinceLast);
                            automationLog.status(AutomationLog.LogStatus.SKIPPED)
                                    .reason("Interval cooldown — "
                                            + (intervalSeconds - secondsSinceLast) + "s remaining");
                            saveLog(automationLog.build());
                            return;
                        }
                    }
                    notificationService.sendNotification("🚀 Triggered: " + automation.getName(), "success");
                    log.info("🚀 Triggered: {}", automation.getName());

                    final AutomationCache finalCache = cache;
                    executeWithTimeout(automation, payload, user, "positive")
                            .thenAccept(success -> {
                                if (!Boolean.TRUE.equals(success)) {
                                    log.warn("⚠️ Positive execution failed for '{}' — cache NOT advanced",
                                            automation.getName());
                                    return;
                                }

                                finalCache.setTriggeredPreviously(true);
                                finalCache.setPreviousExecutionTime(now);
                                finalCache.setLastUpdate(new Date());

                                if (hasDuration) {
                                    finalCache.setState(AutomationState.HOLDING);
                                    redisService.setWithExpiry(
                                            runKey, "active",
                                            condition.getDurationMinutes() * 60L);
                                    log.info("⏱️ Entered HOLDING for {} ({} mins)",
                                            automation.getName(), condition.getDurationMinutes());
                                } else {
                                    finalCache.setState(AutomationState.ACTIVE);
                                    log.info("✅ Entered ACTIVE (no duration): {}",
                                            automation.getName());
                                }

                                redisService.setAutomationCache(cacheKey, finalCache);
                            });

                    automationLog.status(AutomationLog.LogStatus.TRIGGERED)
                            .reason("Condition TRUE → positive actions executed");

                } else if (currentState == AutomationState.ACTIVE
                        || currentState == AutomationState.HOLDING) {

                    boolean shouldRevert = false;
                    String reason = "";
                    if (!conditionNow) {
                        shouldRevert = true;
                        reason = "Condition turned FALSE";
                    } else if (currentState == AutomationState.HOLDING
                            && hasDuration && !durationActive) {
                        shouldRevert = true;
                        reason = "Duration expired";
                    }

                    if (shouldRevert) {
                        notificationService.sendNotification("⏹️ Reverting automation: " + automation.getName(), "success");
                        log.info("⏹️ Reverting automation: {} ({})",
                                automation.getName(), reason);

                        final AutomationCache finalCache = cache;
                        executeWithTimeout(automation, payload, user, "negative")
                                .thenAccept(success -> {
                                    if (!Boolean.TRUE.equals(success)) {
                                        log.warn("⚠️ Negative execution failed for '{}' — cache NOT reset",
                                                automation.getName());
                                        return;
                                    }

                                    finalCache.setState(AutomationState.IDLE);
                                    finalCache.setTriggeredPreviously(false);
                                    finalCache.setLastUpdate(new Date());
                                    redisService.setAutomationCache(cacheKey, finalCache);

                                    log.info("🔁 Reset to IDLE: {}", automation.getName());
                                });
                        automationLog.status(AutomationLog.LogStatus.RESTORED)
                                .reason(reason + " → negative actions executed");
                    } else {
                        automationLog.status(AutomationLog.LogStatus.SKIPPED)
                                .reason("Still ACTIVE/HOLDING — no revert needed");
                    }
                }

                // ❌ NOT MET (IDLE + condition false — normal quiet path)
                else if (currentState == AutomationState.IDLE && !conditionNow) {
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

        executeWithTimeout(automation, payload, user, "positive")
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


    // ─────────────────────────────────────────────────────────────────────────
    // TRIGGER EVALUATION  (unchanged)
    // ─────────────────────────────────────────────────────────────────────────

    private boolean isTriggered(Automation automation, Map<String, Object> payload,
                                boolean wasActive) {
        return isTriggered(automation, payload, wasActive, new ArrayList<>());
    }

    private boolean isTriggered(Automation automation, Map<String, Object> payload,
                                boolean wasActive,
                                List<AutomationLog.ConditionResult> results) {
        if ("time".equals(automation.getTrigger().getType())) {
            var condition = automation.getConditions().get(0);
            boolean passed = isCurrentTimeWithDailyTracking(automation, condition);
            results.add(AutomationLog.ConditionResult.builder()
                    .conditionType("time").conditionNodeId(condition.getNodeId())
                    .triggerKey("time").expectedValue(condition.getTime())
                    .actualValue(LocalTime.now(ZoneId.of("Asia/Kolkata")).toString())
                    .passed(passed)
                    .detail(passed ? "Time matched" : "Time not matched or already fired today")
                    .build());
            return passed;
        }

        var truths = new ArrayList<Boolean>();
        for (var condition : automation.getConditions()) {
            if (!condition.isEnabled()) continue;

            if ("scheduled".equals(condition.getCondition())) {
                boolean passed = isCurrentTimeWithDailyTracking(automation, condition);
                results.add(AutomationLog.ConditionResult.builder()
                        .conditionType("scheduled").conditionNodeId(condition.getNodeId())
                        .triggerKey("schedule")
                        .expectedValue(condition.getScheduleType() + " @ " +
                                ("range".equals(condition.getScheduleType())
                                        ? condition.getFromTime() + " – " + condition.getToTime()
                                        : condition.getTime()))
                        .actualValue(LocalTime.now(ZoneId.of("Asia/Kolkata")).toString())
                        .passed(passed).detail(passed ? "Schedule matched" : "Not matched")
                        .build());
                truths.add(passed);
                continue;
            }

            String key = condition.getTriggerKey();
            if (key == null || key.isBlank() || !payload.containsKey(key)) {
                results.add(AutomationLog.ConditionResult.builder()
                        .conditionType(condition.getCondition())
                        .conditionNodeId(condition.getNodeId()).triggerKey(key).passed(false)
                        .detail("Key '" + key + "' not found in payload").build());
                truths.add(false);
                continue;
            }

            String value = payload.get(key).toString();
            if (isNumeric(value)) {
                double numericValue = Double.parseDouble(value);
                boolean passed = checkCondition(numericValue, condition, wasActive);
                results.add(buildNumericConditionResult(condition, numericValue, passed, wasActive));
                truths.add(passed);
            } else {
                boolean passed = value.equals(condition.getValue());
                results.add(AutomationLog.ConditionResult.builder()
                        .conditionType("equal").conditionNodeId(condition.getNodeId())
                        .triggerKey(key).actualValue(value).expectedValue(condition.getValue())
                        .passed(passed)
                        .detail(passed ? "'" + value + "' equals expected"
                                : "'" + value + "' ≠ '" + condition.getValue() + "'")
                        .build());
                truths.add(passed);
            }
        }

        if (truths.isEmpty()) return false;
        String logic = (automation.getOperators() != null && !automation.getOperators().isEmpty())
                ? automation.getOperators().get(0).getLogicType() : "AND";
        return switch (logic.toUpperCase()) {
            case "OR" -> truths.stream().anyMatch(Boolean::booleanValue);
            default -> truths.stream().allMatch(Boolean::booleanValue);
        };
    }

    private Boolean checkCondition(Double numericValue, Automation.Condition condition,
                                   boolean wasActive) {
        if (condition.getIsExact()) return condition.getValue().equals(numericValue.toString());
        double threshold = Double.parseDouble(condition.getValue());
        double buffer = 5.0;
        return switch (condition.getCondition()) {
            case "above" -> wasActive ? numericValue > (threshold - buffer) : numericValue > threshold;
            case "below" -> wasActive ? numericValue < (threshold + buffer) : numericValue < threshold;
            case "range" -> {
                double above = Double.parseDouble(condition.getAbove());
                double below = Double.parseDouble(condition.getBelow());
                yield wasActive
                        ? numericValue > (above - buffer) && numericValue < (below + buffer)
                        : numericValue > above && numericValue < below;
            }
            default -> false;
        };
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
            String intervalKey = "INTERVAL:" + automation.getId();
            String runKey = "RUNNING:" + automation.getId();
            if (redisService.exists(runKey)) return false;
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
                    "https://api.sunrise-sunset.org/json?lat=17.3850&lng=78.4867&formatted=0",
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

    private CompletableFuture<Boolean> executeWithTimeout(Automation automation,
                                                          Map<String, Object> payload,
                                                          String user, String group) {
        return executeActionsInternal(automation, user, payload, group)
                .orTimeout(AUTOMATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    if (cause instanceof TimeoutException) {
                        log.error("⏱️ Timeout: {}", automation.getName());
                        notificationService.sendNotification(
                                "Automation timeout: " + automation.getName(), "error");
                    } else {
                        log.error("❌ Async error in {}: {}", automation.getName(),
                                cause.getMessage(), cause);
                    }
                    return false;
                });
    }

    private void executeActions(Automation automation, String user, Map<String, Object> payload) {
        executeWithTimeout(automation, payload, user, "positive");
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
            Automation automation,
            String user,
            Map<String, Object> value,
            String groupToRun) {

        List<Automation.Action> orderedActions = automation.getActions().stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsEnabled()))
                .filter(a -> {
                    String group = a.getConditionGroup() != null ? a.getConditionGroup() : "positive";
                    return group.equalsIgnoreCase(groupToRun);
                })
                .sorted(Comparator.comparing(a ->
                        a.getOrder() != 0 ? a.getOrder() : Integer.MAX_VALUE))
                .toList();

        // Fold the action list into a sequential CompletableFuture chain.
        // Starting value: completed future(true) — i.e. "nothing failed yet".
        CompletableFuture<Boolean> chain = CompletableFuture.completedFuture(true);

        for (Automation.Action action : orderedActions) {
            chain = chain.thenCompose(previousOk -> {
                // Run this action on the executor pool
                CompletableFuture<Boolean> actionFuture =
                        CompletableFuture.supplyAsync(() -> {
                            try {
                                log.info("▶️ Executing action: {} (order={})",
                                        action.getName(), action.getOrder());
                                executeSingleAction(action, user, value);
                                return true;
                            } catch (Exception e) {
                                log.error("❌ Failed action: {}", action.getName(), e);
                                return false;  // continue chain even on failure
                            }
                        }, automationExecutor);

                // After the action completes, schedule the delay (if any) without
                // holding a thread — ScheduledExecutorService parks it for free.
                int delaySec = action.getDelaySeconds();
                if (delaySec > 0) {
                    return actionFuture.thenCompose(ok -> {
                        log.info("⏳ Delay {}s after action '{}' (non-blocking)",
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

    private boolean isNumeric(String s) {
        return s != null && s.matches("-?\\d+(\\.\\d+)?");
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

    private AutomationLog.ConditionResult buildNumericConditionResult(
            Automation.Condition condition, double actual, boolean passed, boolean wasActive) {
        double buffer = 5.0;
        String detail, expected;
        switch (condition.getCondition()) {
            case "above" -> {
                double t = Double.parseDouble(condition.getValue());
                expected = "> " + t;
                detail = actual + " > " + (wasActive ? (t - buffer) + " (buf)" : t)
                        + " → " + (passed ? "PASS" : "FAIL");
            }
            case "below" -> {
                double t = Double.parseDouble(condition.getValue());
                expected = "< " + t;
                detail = actual + " < " + (wasActive ? (t + buffer) + " (buf)" : t)
                        + " → " + (passed ? "PASS" : "FAIL");
            }
            case "range" -> {
                expected = condition.getAbove() + " < x < " + condition.getBelow();
                detail = "Value " + actual + " in [" + condition.getAbove()
                        + ", " + condition.getBelow() + "] → " + (passed ? "PASS" : "FAIL");
            }
            default -> {
                expected = condition.getValue();
                detail = actual + " == " + expected + " → " + (passed ? "PASS" : "FAIL");
            }
        }

        return AutomationLog.ConditionResult.builder()
                .conditionType(condition.getCondition())
                .triggerKey(condition.getTriggerKey())
                .actualValue(String.valueOf(actual))
                .expectedValue(expected)
                .passed(passed)
                .detail(detail)
                .build();
    }
}