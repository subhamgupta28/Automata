package dev.automata.automata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.automata.automata.automation.AutomationValidationService;
import dev.automata.automata.dto.AutomationCache;
import dev.automata.automata.dto.LiveEvent;
import dev.automata.automata.model.*;
import dev.automata.automata.modules.Wled;
import dev.automata.automata.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
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

    private ThreadPoolTaskExecutor taskExecutor;

    private final DeviceRepository deviceRepository;
    private final AutomationRepository automationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisService redisService;
    private final MainService mainService;
    private final NotificationService notificationService;
    private final AutomationDetailRepository automationDetailRepository;
    private final DeviceActionStateRepository deviceActionStateRepository;
    private final MessageChannel mqttOutboundChannel;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AutomationLogRepository automationLogRepository;
    private final AutomationValidationService validationService;
    private static final String TOPIC_ACTION = "action/";

    // Timeout and lock constants
    private static final long AUTOMATION_TIMEOUT_SECONDS = 30;
    private static final long LOCK_TTL_SECONDS = 60;

    // FIX #7: Removed broken acquireLock()/ThreadLocal pattern.
    // All locking now goes through withLock() which correctly scopes lockValue.

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

    @Scheduled(fixedRate = 65000)
    public void pollWledState() {
        List<Device> wledDevices = mainService.getAllDevice();
        for (Device device : wledDevices) {
            if (device.getType().equals("WLED")) {
                var led = new Wled(mqttOutboundChannel, device);
                led.publishForInfo(device.getId());
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUBLIC API
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
        var automation = automationRepository.findById(id).orElse(null);
        if (automation != null) {
            automation.setIsEnabled(enabled);
            automationRepository.save(automation);
            notificationService.sendNotification("Automation updated", "success");
        }
        updateRedisStorage();
        return "success";
    }

    public String ackAction(String deviceId, Map<String, Object> payload) {
        if (payload.containsKey("actionAck")) {
            messagingTemplate.convertAndSend("/topic/data", Map.of("deviceId", deviceId, "ack", payload));
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
            notifyBasedOnResult(result);
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
                executeActions(automation, user, new HashMap<>());
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
                System.out.println("Master action sent " + req);
                System.out.println("Device type " + device.get().getType());
                handleAction(id, req, device.get().getType(), user);
            }
        }

        if (payload.containsKey("direct")) {
            var check = payload.get("direct").toString();
            if (Boolean.parseBoolean(check)) {
                sendDirectAction(deviceId, payload);
                return "No saved action found but sent directly";
            } else {
                //TODO: handle for direct=false
            }
        }

        var automations = automationRepository.findByTrigger_DeviceId(deviceId);
        System.err.println("Automations found: ");
        automations.forEach(a -> {
            System.err.println(a.getName());
            checkAndExecuteSingleAutomation(a, payload, user);
        });

        notificationService.sendNotification("Action applied", "success");
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
        var automations = automationRepository.findByTrigger_DeviceId(deviceId);
        var payload = new HashMap<String, Object>();
        var keyJoiner = new StringJoiner(",");

        for (Automation automation : automations) {
            String key = automation.getTrigger().getKey();
            var conditions = automation.getConditions();
            if (conditions == null || conditions.isEmpty()) continue;

            var condition = conditions.get(0);
            String pKey = automation.getId();
            String pVal = key;

            if (Boolean.TRUE.equals(condition.getIsExact())) {
                pVal += "=" + condition.getValue();
            } else {
                pVal += ">" + condition.getAbove() + ",<" + condition.getBelow();
            }

            payload.put(pKey, pVal);
            keyJoiner.add(pKey);
        }

        payload.put("keys", keyJoiner.toString());
        messagingTemplate.convertAndSend("/topic/action." + deviceId, payload);
        sendToTopic(TOPIC_ACTION + deviceId, payload);
        return payload;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SAVE AUTOMATION
    // ─────────────────────────────────────────────────────────────────────────

    public String saveAutomationDetailWithValidation(AutomationDetail detail) {
        log.info("Validating automation detail...");
        List<String> validationErrors = validationService.validate(detail);
        if (!validationErrors.isEmpty()) {
            log.error("Automation validation failed: {}", validationErrors);
            notificationService.sendNotification(
                    "Validation failed: " + String.join(", ", validationErrors), "error");
            return "validation_failed: " + String.join("; ", validationErrors);
        }
        return saveAutomationDetailInternal(detail);
    }

    public String saveAutomationDetail(AutomationDetail detail) {
        System.err.println(detail);
        return saveAutomationDetailInternal(detail);
    }

    private String saveAutomationDetailInternal(AutomationDetail detail) {
        log.info("Saving automation detail: {}", detail);

        var automationBuilder = Automation.builder()
                .isEnabled(true)
                .isActive(false);

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
                            tData.getName(), tData.getPriority()
                    ));
                    automationBuilder.name(tData.getName());
                });

        var actions = detail.getNodes().stream()
                .filter(n -> n.getData().getActionData() != null)
                .map(n -> {
                    var a = n.getData().getActionData();
                    return new Automation.Action(
                            a.getKey(), a.getDeviceId(), a.getData(),
                            a.getName(), a.getIsEnabled(), a.getRevert(),
                            a.getConditionGroup()
                    );
                }).toList();
        automationBuilder.actions(actions);

        var conditionList = detail.getNodes().stream()
                .map(n -> n.getData().getConditionData())
                .filter(Objects::nonNull)
                .map(c -> new Automation.Condition(
                        c.getCondition(), c.getValueType(), c.getAbove(), c.getBelow(),
                        c.getValue(), c.getTime(), c.getTriggerKey(), c.getIsExact(),
                        c.getScheduleType(), c.getFromTime(), c.getToTime(), c.getDays()
                ))
                .collect(Collectors.toList());
        automationBuilder.conditions(conditionList);

        var operators = detail.getNodes().stream()
                .map(n -> n.getData().getOperators())
                .filter(Objects::nonNull)
                .map(c -> new Automation.Operator(c.getType(), c.getLogicType()))
                .collect(Collectors.toList());
        automationBuilder.operators(operators);

        var automation = automationBuilder.build();
        var device = mainService.getDevice(automation.getTrigger().getDeviceId());
        automation.setTriggerDeviceType(device.getType());

        var saved = automationRepository.save(automation);
        detail.setId(saved.getId());
        automationDetailRepository.save(detail);

        notificationService.sendNotification("Automation saved successfully", "success");
        updateRedisStorage();
        return "success";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SCHEDULED JOBS
    // ─────────────────────────────────────────────────────────────────────────
    @Scheduled(fixedRate = 32000)
    private void triggerPeriodicAutomations() {
        // Pull from Redis cache instead of Mongo on every tick
        List<CompletableFuture<Void>> futures = automationRepository.findEnabledForExecution()
                .stream()
                .map(a -> CompletableFuture.runAsync(() -> {
                    // Prefer cached data from Redis
                    AutomationCache cached = redisService.getAutomationCache(
                            a.getTrigger().getDeviceId() + ":" + a.getId());
                    Automation toRun = (cached != null && cached.getAutomation() != null)
                            ? cached.getAutomation() : a;

                    checkAndExecuteSingleAutomation(
                            toRun,
                            redisService.getRecentDeviceData(a.getTrigger().getDeviceId()),
                            "system"
                    );
                }, automationExecutor))
                .toList();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

//    @Scheduled(fixedRate = 15000)
//    private void triggerPeriodicAutomations() {
//        List<CompletableFuture<Void>> futures = automationRepository.findByIsEnabledTrue()
//                .stream()
//                .map(a -> CompletableFuture.runAsync(() ->
//                        checkAndExecuteSingleAutomation(
//                                a,
//                                redisService.getRecentDeviceData(a.getTrigger().getDeviceId()),
//                                "system"
//                        ), automationExecutor))
//                .toList();
//
//        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
//    }

    /**
     * FIX #5: updateRedisStorage now guards against overwriting recently-updated
     * cache entries using a lastUpdate timestamp comparison.
     * Entries updated within the last 5 minutes by live execution are skipped.
     */
    @Scheduled(fixedRate = 1000 * 60 * 5)
    private void updateRedisStorage() {
        Date fiveMinutesAgo = new Date(System.currentTimeMillis() - 5 * 60 * 1000);

        automationRepository.findAll().forEach(a -> {
            var id = a.getTrigger().getDeviceId() + ":" + a.getId();
            AutomationCache existing = redisService.getAutomationCache(id);
            if (existing == null) return;

            // FIX #5: Don't overwrite if execution just updated the cache
            if (existing.getLastUpdate() != null && existing.getLastUpdate().after(fiveMinutesAgo)) {
                log.debug("Skipping Redis refresh for {} — updated recently", a.getName());
                return;
            }

            AutomationCache updatedCache = AutomationCache.builder()
                    .id(a.getId())
                    .automation(a)
                    .triggerDeviceType(a.getTriggerDeviceType())
                    .enabled(a.getIsEnabled())
                    .triggerDeviceId(a.getTrigger().getDeviceId())
                    .isActive(existing.getIsActive())
                    .triggeredPreviously(existing.isTriggeredPreviously())
                    .previousExecutionTime(existing.getPreviousExecutionTime())
                    .lastUpdate(existing.getLastUpdate())
                    .build();

            redisService.setAutomationCache(id, updatedCache);
        });
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CORE AUTOMATION EXECUTION
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * FIX #2: Idempotency key now only gates the TRIGGERED path, not the full method.
     * This allows the RESTORED / revert path to always run correctly.
     * FIX #3: Lock contention now falls back gracefully instead of silently skipping.
     * FIX #4: Cache is written inside thenRun() so it only updates after actions succeed.
     */
    public void checkAndExecuteSingleAutomation(
            Automation automation,
            Map<String, Object> data,
            String user) {

        Date now = new Date();

        var payload = new HashMap<String, Object>();
        var deviceId = automation.getTrigger().getDeviceId();

        if (data != null) payload.putAll(data);
        else payload.putAll(mainService.getLastData(deviceId));

        String cacheKey = deviceId + ":" + automation.getId();

        // FIX #3: Try lock, but fall back gracefully on contention
        AutomationCache automationCache = withLock(
                "LOCK:CACHE:" + cacheKey, LOCK_TTL_SECONDS,
                () -> {
                    AutomationCache cache = redisService.getAutomationCache(cacheKey);
                    if (cache == null) {
                        cache = AutomationCache.builder()
                                .id(automation.getId())
                                .triggeredPreviously(false)
                                .previousExecutionTime(null)
                                .lastUpdate(new Date())
                                .build();
                    }
                    return cache;
                }
        );

        // FIX #3: Fall back to direct read instead of silently returning null
        if (automationCache == null) {
            log.warn("Lock contention for automation: {} — falling back to direct cache read", automation.getName());
            automationCache = redisService.getAutomationCache(cacheKey);
            if (automationCache == null) {
                automationCache = AutomationCache.builder()
                        .id(automation.getId())
                        .triggeredPreviously(false)
                        .previousExecutionTime(null)
                        .lastUpdate(new Date())
                        .build();
            }
        }

        List<AutomationLog.ConditionResult> conditionResults = new ArrayList<>();
        boolean isTriggeredNow = isTriggered(automation, payload, automationCache.isTriggeredPreviously(), conditionResults);

        String triggerType = automation.getTrigger().getType();
        String operatorLogic = "AND";
        if (automation.getOperators() != null && !automation.getOperators().isEmpty()) {
            operatorLogic = automation.getOperators().get(0).getLogicType();
        }

        long diff = 0;
        if (automationCache.getPreviousExecutionTime() != null) {
            diff = now.toInstant().getEpochSecond()
                    - automationCache.getPreviousExecutionTime().toInstant().getEpochSecond();
        }

//        if (diff > 60 * 3) {
//            automationCache.setTriggeredPreviously(false);
//        }

        var automationLog = AutomationLog.builder()
                .automationId(automation.getId())
                .automationName(automation.getName())
                .conditionResults(conditionResults)
                .operatorLogic(operatorLogic)
                .payload(payload)
                .triggerType(triggerType)
                .triggerDeviceId(deviceId)
                .timestamp(now);

        if (isTriggeredNow && !automationCache.isTriggeredPreviously()) {

            // FIX #2: Idempotency check ONLY on the triggered path, not at method top.
            String idempotencyKey = generateIdempotencyKey(automation, now);
            if (isAlreadyExecuted(idempotencyKey)) {
                log.debug("Skipping duplicate TRIGGERED execution for: {}", automation.getName());
                automationLog.status(AutomationLog.LogStatus.SKIPPED)
                        .reason("Idempotency key already set — duplicate trigger within same minute");
                saveLog(automationLog.build());
                return;
            }

            log.info("🚀 Automation Triggered: {}", automation.getName());
            notificationService.sendNotification("Executing automation: " + automation.getName(), "low");

            if (!"time".equals(triggerType)) {
                withLock("LOCK:SNAPSHOT:" + automation.getId(), LOCK_TTL_SECONDS, () -> {
                    saveStateSnapshots(automation);
                    return null;
                });
            }

            // Capture final references for lambda
            final AutomationCache finalCache = automationCache;
            final String finalOperatorLogic = operatorLogic;

            // FIX #4: Cache is written INSIDE thenRun — only after actions complete successfully.
            executeWithTimeout(automation, payload, user, "positive")
                    .thenRun(() -> {
                        markAsExecuted(idempotencyKey);

                        finalCache.setTriggeredPreviously(true);
                        finalCache.setPreviousExecutionTime(now);
                        finalCache.setLastUpdate(new Date());
                        redisService.setAutomationCache(cacheKey, finalCache);

                        log.info("✅ Automation completed and cache updated: {}", automation.getName());
                    })
                    .exceptionally(ex -> {
                        log.error("❌ Automation failed — cache NOT updated, will retry: {}", automation.getName(), ex);
                        return null;
                    });

            automationLog.status(AutomationLog.LogStatus.TRIGGERED)
                    .reason("All conditions met (" + operatorLogic + ") — actions executed");

        } else if (!isTriggeredNow && automationCache.isTriggeredPreviously()) {
            log.info("🔄 Automation Cleared: Running negative actions for {}", automation.getName());
            notificationService.sendNotification("Restoring automation: " + automation.getName(), "low");

            // Run negative-group actions directly instead of snapshot restore
            AutomationCache finalAutomationCache = automationCache;
            executeWithTimeout(automation, payload, user, "negative")
                    .thenRun(() -> {
                        finalAutomationCache.setTriggeredPreviously(false);
                        finalAutomationCache.setPreviousExecutionTime(null);
                        finalAutomationCache.setLastUpdate(new Date());
                        redisService.setAutomationCache(cacheKey, finalAutomationCache);
                    });

            automationLog.status(AutomationLog.LogStatus.RESTORED)
                    .reason("Conditions cleared — negative actions executed");

        } else if (!isTriggeredNow) {
            automationLog.status(AutomationLog.LogStatus.NOT_MET)
                    .reason("Conditions not satisfied — no action taken");

        } else {
            automationLog.status(AutomationLog.LogStatus.SKIPPED)
                    .reason("Conditions still met but already triggered — cooldown active (diff=" + diff + "s)");
        }

        saveLog(automationLog.build());
        log.debug("Automation log — Name: {} Status: {} Reason: {}",
                automation.getName(),
                automationLog.build().getStatus(),
                automationLog.build().getReason());
    }

    private void executeAutomationImmediate(Automation automation, Map<String, Object> payload, String user) {
        if (automation.getIsEnabled()) {
            var deviceId = automation.getTrigger().getDeviceId();
            Date now = new Date();
            var automationLog = AutomationLog.builder()
                    .automationId(automation.getId())
                    .automationName(automation.getName())
                    .conditionResults(new ArrayList<>())
                    .operatorLogic("")
                    .payload(payload)
                    .triggerType(automation.getTrigger().getType())
                    .triggerDeviceId(deviceId)
                    .timestamp(now)
                    .status(AutomationLog.LogStatus.USER_OVERRIDE)
                    .reason("Automation triggered manually by user: " + user);

            executeActions(automation, user, payload);
            saveLog(automationLog.build());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TRIGGER EVALUATION
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * FIX #1 + #2: isCurrentTimeWithDailyTracking now only applies the daily-fire
     * lock for "at" (point-in-time) schedules. Range schedules act as a continuous
     * gate using wasTriggeredPreviously, matching user intent.
     * <p>
     * FIX #1 (overnight range): For overnight ranges the daily key is computed from
     * the range START date, so a midnight rollover doesn't re-arm a key set at 10 PM.
     */
    private boolean isCurrentTimeWithDailyTracking(Automation automation, Automation.Condition condition) {
        ZoneId istZone = ZoneId.of("Asia/Kolkata");
        ZonedDateTime nowZdt = ZonedDateTime.now(istZone);
        LocalTime current = nowZdt.toLocalTime();

        // Day-of-week check
        if (condition.getDays() != null && !condition.getDays().isEmpty()) {
            String dayOfWeek = nowZdt.getDayOfWeek()
                    .getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)
                    .substring(0, 3);
            dayOfWeek = dayOfWeek.substring(0, 1).toUpperCase() + dayOfWeek.substring(1).toLowerCase();
            if (!condition.getDays().contains(dayOfWeek)) return false;
        }

        String scheduleType = condition.getScheduleType();

        if ("range".equals(scheduleType)) {
            // FIX #1: Range schedules are pure time-window gates — no daily lock.
            // The wasTriggeredPreviously + revert flow in checkAndExecuteSingleAutomation
            // already prevents repeated firing within a single active window.
            LocalTime from = parseTime(condition.getFromTime());
            LocalTime to = parseTime(condition.getToTime());
            if (from == null || to == null) return false;

            if (from.isBefore(to)) {
                // Normal range e.g. 08:00 – 20:30
                return !current.isBefore(from) && !current.isAfter(to);
            } else {
                // FIX #1 overnight: e.g. 22:00 – 06:00
                // No double-trigger risk since no daily lock is used.
                return !current.isBefore(from) || !current.isAfter(to);
            }
        }

        // "at" schedule: exact time match within 1 minute, fire once per day
        LocalTime target = parseTime(condition.getTime());
        if (target == null) return false;
        boolean timeMatches = Math.abs(ChronoUnit.MINUTES.between(target, current)) <= 1;
        if (!timeMatches) return false;

        // Daily-fire lock only for "at" schedules
        LocalDate today = nowZdt.toLocalDate();
        String dailyKey = String.format("DAILY_FIRE:%s:%s", automation.getId(), today);
        if (redisService.exists(dailyKey)) {
            log.debug("Automation {} already fired today at target {}", automation.getName(), target);
            return false;
        }

        long secondsUntilMidnight = ChronoUnit.SECONDS.between(
                nowZdt, nowZdt.plusDays(1).truncatedTo(ChronoUnit.DAYS));
        redisService.setWithExpiry(dailyKey, "fired", secondsUntilMidnight);
        log.info("✅ Daily automation {} fired at {}", automation.getName(), current);
        return true;
    }

    private boolean isTriggered(Automation automation, Map<String, Object> payload, boolean wasActive) {
        return isTriggered(automation, payload, wasActive, new ArrayList<>());
    }

    private boolean isTriggered(
            Automation automation,
            Map<String, Object> payload,
            boolean wasActive,
            List<AutomationLog.ConditionResult> results) {

        if ("time".equals(automation.getTrigger().getType())) {
            var condition = automation.getConditions().get(0);
            boolean passed = isCurrentTimeWithDailyTracking(automation, condition);
            results.add(AutomationLog.ConditionResult.builder()
                    .conditionType("time")
                    .triggerKey("time")
                    .expectedValue(condition.getTime())
                    .actualValue(LocalTime.now(ZoneId.of("Asia/Kolkata")).toString())
                    .passed(passed)
                    .detail(passed ? "Time matched" : "Time not matched or already fired today")
                    .build());
            return passed;
        }

        var conditions = automation.getConditions();
        var operators = automation.getOperators();
        var truths = new ArrayList<Boolean>();

        for (var condition : conditions) {
            if ("scheduled".equals(condition.getCondition())) {
                // FIX #1: Uses updated daily-tracking method
                boolean passed = isCurrentTimeWithDailyTracking(automation, condition);
                results.add(AutomationLog.ConditionResult.builder()
                        .conditionType("scheduled")
                        .triggerKey("schedule")
                        .expectedValue(condition.getScheduleType() + " @ " +
                                ("range".equals(condition.getScheduleType())
                                        ? condition.getFromTime() + " – " + condition.getToTime()
                                        : condition.getTime()))
                        .actualValue(LocalTime.now(ZoneId.of("Asia/Kolkata")).toString())
                        .passed(passed)
                        .detail(passed ? "Schedule matched" :
                                "Schedule not matched — days/time mismatch or already fired today")
                        .build());
                truths.add(passed);
                continue;
            }

            String key = condition.getTriggerKey();
            if (key == null || key.isBlank() || !payload.containsKey(key)) {
                results.add(AutomationLog.ConditionResult.builder()
                        .conditionType(condition.getCondition())
                        .triggerKey(key)
                        .passed(false)
                        .detail("Key '" + key + "' not found in payload")
                        .build());
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
                        .conditionType("equal")
                        .triggerKey(key)
                        .actualValue(value)
                        .expectedValue(condition.getValue())
                        .passed(passed)
                        .detail(passed ? "'" + value + "' equals expected" :
                                "'" + value + "' does not equal '" + condition.getValue() + "'")
                        .build());
                truths.add(passed);
            }
        }

        if (truths.isEmpty()) return false;

        String globalLogic = (operators != null && !operators.isEmpty())
                ? operators.get(0).getLogicType() : "AND";

        return switch (globalLogic.toUpperCase()) {
            case "OR" -> truths.stream().anyMatch(Boolean::booleanValue);
            case "AND" -> truths.stream().allMatch(Boolean::booleanValue);
            default -> truths.stream().allMatch(Boolean::booleanValue);
        };
    }

    private Boolean checkCondition(Double numericValue, Automation.Condition condition, boolean wasActive) {
        if (condition.getIsExact()) {
            return condition.getValue().equals(numericValue.toString());
        }

        double threshold = Double.parseDouble(condition.getValue());
        double buffer = 5.0;

        switch (condition.getCondition()) {
            case "above" -> {
                if (wasActive) return numericValue > (threshold - buffer);
                return numericValue > threshold;
            }
            case "below" -> {
                if (wasActive) return numericValue < (threshold + buffer);
                return numericValue < threshold;
            }
            case "range" -> {
                double above = Double.parseDouble(condition.getAbove());
                double below = Double.parseDouble(condition.getBelow());
                if (wasActive) return numericValue > (above - buffer) && numericValue < (below + buffer);
                return numericValue > above && numericValue < below;
            }
        }
        return false;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ACTION EXECUTION
    // ─────────────────────────────────────────────────────────────────────────

    private CompletableFuture<Void> executeWithTimeout(
            Automation automation, Map<String, Object> payload,
            String user, String group) {

        return CompletableFuture.runAsync(() -> {
                    try {
                        executeActionsInternal(automation, user, payload, group);
                    } catch (Exception e) {
                        log.error("Error executing automation {}: {}", automation.getName(), e.getMessage(), e);
                        throw new RuntimeException(e);
                    }
                }, automationExecutor)
                .orTimeout(AUTOMATION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    if (ex instanceof TimeoutException) {
                        log.error("⏱️ Automation {} timed out", automation.getName());
                        notificationService.sendNotification("Automation timeout: " + automation.getName(), "error");
                    }
                    return null;
                });
    }

    private void executeActions(Automation automation, String user, Map<String, Object> payload) {
        executeWithTimeout(automation, payload, user, "positive");
    }

    private void executeActionsInternal(
            Automation automation, String user,
            Map<String, Object> value, String groupToRun) {

        for (Automation.Action action : automation.getActions()) {
            if (Boolean.FALSE.equals(action.getIsEnabled())) continue;

            // Only run actions belonging to the requested group
            String actionGroup = action.getConditionGroup() != null
                    ? action.getConditionGroup() : "positive";
            if (!actionGroup.equalsIgnoreCase(groupToRun)) continue;

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
                notificationService.sendNotify("Automation",
                        action.getData() + " and live data are " + value, "low");
            } else if ("WLED".equals(mainService.getDevice(action.getDeviceId()).getType())) {
                handleWLED(action.getDeviceId(), new HashMap<>(payload), user);
            } else {
                deviceActionStateRepository.save(DeviceActionState.builder()
                        .user(user)
                        .deviceId(action.getDeviceId())
                        .timestamp(Date.from(ZonedDateTime.now(ZoneId.systemDefault()).toInstant()))
                        .payload(payload)
                        .deviceType("sensor")
                        .build());
                messagingTemplate.convertAndSend("/topic/action." + action.getDeviceId(), payload);
                sendToTopic(TOPIC_ACTION + action.getDeviceId(), payload);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // STATE SNAPSHOTS
    // ─────────────────────────────────────────────────────────────────────────

    private void saveStateSnapshots(Automation automation) {
        for (Automation.Action action : automation.getActions()) {
            String targetDeviceId = action.getDeviceId();
            Map<String, Object> currentState = (Map<String, Object>) mainService.getLastData(targetDeviceId);
            if (currentState != null && !currentState.isEmpty()) {
                String snapshotKey = "SNAPSHOT:" + automation.getId() + ":" + targetDeviceId;
                redisService.setRecentDeviceData(snapshotKey, currentState);
                System.out.println("📸 Snapshot saved for device: " + targetDeviceId + currentState);
            }
        }
    }

    private void restoreStateSnapshots(Automation automation, String user) {
        for (Automation.Action action : automation.getActions()) {
            if (!action.getRevert()) continue;

            String targetDeviceId = action.getDeviceId();
            String snapshotKey = "SNAPSHOT:" + automation.getId() + ":" + targetDeviceId;
            Map<String, Object> previousState = (Map<String, Object>) redisService.getRecentDeviceData(snapshotKey);

            if (previousState != null) {
                System.out.println("⏪ Restoring device: " + targetDeviceId + previousState);
                var device = deviceRepository.findById(targetDeviceId).orElse(null);
                if (device == null) continue;

                if ("WLED".equals(device.getType())) {
                    restoreWledState(targetDeviceId, previousState, user);
                } else {
                    messagingTemplate.convertAndSend("/topic/action." + targetDeviceId, previousState);
                    sendToTopic(TOPIC_ACTION + targetDeviceId, previousState);
                }
            }
        }
    }

    private void restoreWledState(String deviceId, Map<String, Object> state, String user) {
        handleWLED(deviceId, state, user);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // REDIS HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * FIX #7: withLock is the ONLY locking primitive. acquireLock() with ThreadLocal
     * has been removed entirely. lockValue is correctly scoped within this method.
     */
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

    private String generateIdempotencyKey(Automation automation, Date executionTime) {
        long roundedMinute = executionTime.toInstant().getEpochSecond() / 60;
        return String.format("IDEMPOTENCY:%s:%d", automation.getId(), roundedMinute);
    }

    private boolean isAlreadyExecuted(String idempotencyKey) {
        return redisService.exists(idempotencyKey);
    }

    private void markAsExecuted(String idempotencyKey) {
        redisService.setWithExpiry(idempotencyKey, "executed", 7200); // 2 hours
    }

    /**
     * FIX #6: TTL corrected to 60 seconds (was mistakenly 60*60 = 3600).
     * Comment updated to match actual behavior.
     */
    private void saveLog(AutomationLog log) {
        if (log.getStatus() == AutomationLog.LogStatus.NOT_MET) {
            String debounceKey = "LOG_DEBOUNCE:" + log.getAutomationId();
            if (redisService.exists(debounceKey)) return;
            redisService.setWithExpiry(debounceKey, "1", 60); // FIX #6: 60 seconds, not 3600
        }
        automationLogRepository.save(log);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DEVICE / MQTT HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    private void sendToTopic(String topic, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            mqttOutboundChannel.send(
                    MessageBuilder.withPayload(json)
                            .setHeader("mqtt_topic", topic)
                            .build()
            );
            System.out.println("📤 Sent to " + topic + " => " + json);
        } catch (Exception e) {
            System.err.println(e);
        }
    }

    private void notifyBasedOnResult(String result) {
        if ("success".equals(result)) {
            notificationService.sendNotification("Action applied", "success");
        } else {
            notificationService.sendNotification("Action failed", "error");
        }
    }

    private String rebootDevice(Device device) {
        if (device == null) return "Device not found";
        RestTemplate restTemplate = new RestTemplate();
        Map<String, Object> map = Map.of("deviceId", device.getId(), "reboot", true, "key", "reboot");
        messagingTemplate.convertAndSend("/topic/action." + device.getId(), map);
        sendToTopic(TOPIC_ACTION + device.getId(), map);
        try {
            var res = restTemplate.getForObject(device.getAccessUrl() + "/restart", String.class);
            System.err.println(res);
        } catch (Exception e) {
            notificationService.sendNotification("Reboot action failed for device: " + device.getName(), "error");
            System.err.println(e.getMessage());
        }
        return "Rebooting device";
    }

    private void sendDirectAction(String deviceId, Map<String, Object> payload) {
        var map = new HashMap<String, Object>();
        var key = payload.get("key").toString();
        map.put(key, payload.get(key));
        map.put("key", key);
        messagingTemplate.convertAndSend("/topic/action." + deviceId, map);
        sendToTopic(TOPIC_ACTION + deviceId, map);
        notificationService.sendNotification("Action applied", "success");
    }

    private String handleWLED(String deviceId, Map<String, Object> payload, String user) {
        var device = deviceRepository.findById(deviceId).orElse(null);
        if (device == null) return "Not found";
        try {
            var wled = new Wled(mqttOutboundChannel, device);
            return wled.handleAction(payload);
        } catch (Exception e) {
            System.err.println(e);
            return "Error";
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // MISC HELPERS
    // ─────────────────────────────────────────────────────────────────────────

    @EventListener
    public void onCustomEvent(LiveEvent event) {
        var payload = event.getPayload();
        var deviceId = payload.get("device_id").toString();
        redisService.setRecentDeviceData(deviceId, payload);
    }

    private boolean isNumeric(String input) {
        return input != null && input.matches("-?\\d+(\\.\\d+)?");
    }

    private boolean isText(String input) {
        return input != null && input.matches("[a-zA-Z]+");
    }

    private Object parseData(String data) {
        if (data == null) return null;
        if ("true".equalsIgnoreCase(data)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(data)) return Boolean.FALSE;
        try {
            if (data.contains(".")) return Double.parseDouble(data);
            else return Integer.parseInt(data);
        } catch (NumberFormatException ignored) {
        }
        return data;
    }

    private LocalTime parseTime(String timeText) {
        if (timeText == null || timeText.isBlank()) return null;
        try {
            return LocalTime.parse(timeText.trim(), DateTimeFormatter.ofPattern("HH:mm:ss"));
        } catch (Exception e1) {
            try {
                DateTimeFormatter fmt12 = new DateTimeFormatterBuilder()
                        .parseCaseInsensitive()
                        .appendPattern("hh:mm:ss a")
                        .toFormatter(Locale.ENGLISH);
                return LocalTime.parse(timeText.trim(), fmt12);
            } catch (Exception e2) {
                System.err.println("⚠️ Unable to parse time: '" + timeText + "'");
                return null;
            }
        }
    }

    private AutomationLog.ConditionResult buildNumericConditionResult(
            Automation.Condition condition, double actual, boolean passed, boolean wasActive) {

        double buffer = 5.0;
        String detail;
        String expected;

        switch (condition.getCondition()) {
            case "above" -> {
                double threshold = Double.parseDouble(condition.getValue());
                expected = "> " + threshold;
                detail = actual + " > " + (wasActive ? (threshold - buffer) + " (with buffer)" : threshold)
                        + " → " + (passed ? "PASS" : "FAIL");
            }
            case "below" -> {
                double threshold = Double.parseDouble(condition.getValue());
                expected = "< " + threshold;
                detail = actual + " < " + (wasActive ? (threshold + buffer) + " (with buffer)" : threshold)
                        + " → " + (passed ? "PASS" : "FAIL");
            }
            case "range" -> {
                expected = condition.getAbove() + " < x < " + condition.getBelow();
                detail = "Value " + actual + " in range [" + condition.getAbove()
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