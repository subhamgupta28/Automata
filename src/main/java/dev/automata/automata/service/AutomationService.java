package dev.automata.automata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.automata.automata.automation.AutomationValidationService;
import dev.automata.automata.automation.PeriodicCheckEvent;
import dev.automata.automata.automation.ScheduledAutomationManager;
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
    private final ApplicationEventPublisher eventPublisher;
    private final AutomationLogBuffer logBuffer;
    private final ActionDeliveryTracker deliveryTracker;
    private final ScheduledAutomationManager scheduledAutomationManager;

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
//            messagingTemplate.convertAndSend("/topic/data",
//                    Map.of("deviceId", deviceId, "ack", payload));
            if (payload.containsKey("actionType")) {
                notificationService.sendNotification("Action sent to device", "success");
            }

            // Confirm delivery if the ack includes a correlationId
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
                .forEach(a -> checkAndExecuteSingleAutomation(a, payload, user));

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
        redisService.setRecentDeviceData(payload.get("device_id").toString(), payload);
    }

    /**
     * Returns the list of enabled automations for a device, served from a
     * short-lived Redis cache (TTL = 5 min) to avoid hitting MongoDB on every
     * live-data event (15+ devices * 1 event/sec = 15 MongoDB queries/sec otherwise).
     * <p>
     * The cache is invalidated in refreshCacheForAutomation() whenever an
     * automation is saved or toggled, so staleness is bounded by save operations.
     */
    private List<Automation> getCachedAutomationsForDevice(String deviceId) {
        String key = "AUTOMATIONS_BY_DEVICE:" + deviceId;
        try {
            Object cached = redisService.get(key);
            if (cached != null) {
                // Redis stores it as a JSON string via ObjectMapper
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
        if (!featureService.isFeatureEnabled("PERIODIC_AUTOMATION_SERVICE")) {
            log.error("Automation service is currently disabled.");
            return;
        }
        automationRepository.findEnabledForExecution().stream()
                .filter(a -> !scheduledAutomationManager.hasOnlyScheduledConditions(a))
                .forEach(a -> {
                    AutomationCache cached = redisService.getAutomationCache(a.getTrigger().getDeviceId() + ":" + a.getId());
                    Automation toRun = (cached != null && cached.getAutomation() != null) ? cached.getAutomation() : a;
                    Map<String, Object> recentData = redisService.getRecentDeviceData(a.getTrigger().getDeviceId());
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
                            t.getName(), t.getPriority(), t.getNodeId()));
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
                                c.getDurationMinutes(), c.isEnabled(), c.getPreviousNodeRef()))
                        .collect(Collectors.toList()));

        automationBuilder.operators(
                detail.getNodes().stream()
                        .map(n -> n.getData().getOperators())
                        .filter(Objects::nonNull)
                        .map(c -> new Automation.Operator(
                                c.getType(), c.getLogicType(),
                                c.getPreviousNodeRef(), c.getNodeId(),
                                c.getPriority()))          // ← priority preserved
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

    private boolean hasDataDrivenCondition(Automation automation) {
        if (automation.getConditions() == null) return false;
        Set<String> operatorIds = automation.getOperators().stream()
                .map(Automation.Operator::getNodeId)
                .collect(Collectors.toSet());
        // trigger conditions (non-gate) that are NOT "scheduled"
        return automation.getConditions().stream()
                .filter(Automation.Condition::isEnabled)
                .filter(c -> !isGateCondition(c, operatorIds))
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
    // CORE EXECUTION  — new per-branch model
    // ═════════════════════════════════════════════════════════════════════

    public void checkAndExecuteSingleAutomation(Automation automation,
                                                Map<String, Object> data,
                                                String user) {
        Date now = new Date();
        String deviceId = automation.getTrigger().getDeviceId();
        String cacheKey = deviceId + ":" + automation.getId();

        // ── Execution lock (UUID-safe) ────────────────────────────────────
        String lockKey = "EXEC_LOCK:" + automation.getId();
        String lockValue = UUID.randomUUID().toString();          // ← fix: UUID, not "locked"
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
            // ── Snooze / timed-disable gate ───────────────────────────────
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

            // ── Prepare payload ───────────────────────────────────────────
            Map<String, Object> payload = new HashMap<>();
            if (data != null) payload.putAll(data);
            else payload.putAll(mainService.getLastData(deviceId));

            // ── Load / init cache ─────────────────────────────────────────
            AutomationCache cache = loadOrInitCache(cacheKey, automation);

            // ── Evaluate the full graph ───────────────────────────────────
            ExecutionContext ctx = new ExecutionContext();
            NodeResult rootResult = evaluate(automation, payload, ctx, cache);   // populates ctx for all nodes
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
                saveLog(logBuilder
                        .conditionResults(buildConditionResults(automation, ctx, payload))
                        .status(AutomationLog.LogStatus.TRIGGERED)
                        .reason("Stateless actions fired: " + actionSummary(noneActions))
                        .build());
                return;
            }
            // ── Classify conditions ───────────────────────────────────────
            Set<String> operatorIds = automation.getOperators() == null ? Set.of() :
                    automation.getOperators().stream()
                    .map(Automation.Operator::getNodeId)
                    .collect(Collectors.toSet());

            List<Automation.Condition> triggerConditions = automation.getConditions().stream()
                    .filter(Automation.Condition::isEnabled)
                    .filter(c -> !isGateCondition(c, operatorIds))
                    .toList();

            // ── Phase 1: c1 (trigger conditions) ─────────────────────────
            // All trigger conditions combined with implicit AND (same as before).
            boolean c1True = rootResult != null && rootResult.isTrue();
//            boolean c1True = triggerConditions.stream()
//                    .map(c -> ctx.get(c.getNodeId()))
//                    .filter(Objects::nonNull)
//                    .allMatch(NodeResult::isTrue);


            if (!c1True) {
                // ── c1 FALSE ─────────────────────────────────────────────
                // 1. Fire informational actions (notifications, alerts) unconditionally.
                // 2. If any branch was ACTIVE, fire the c1-negative actions
                //    (e.g. "lux out of range — dim everything") and reset all branches.
                //    These are actions whose previousNodeRef points to a trigger-condition
                //    node with a cond-negative handle.
                // No state machine advancement — branches stay / go IDLE.

                List<Automation.Action> informational = resolveInformationalActions(automation, ctx);
                if (!informational.isEmpty()) {
                    log.info("ℹ️ [{}] [{}] trigger condition not met — firing {} informational action(s)", user,
                            automation.getName(), informational.size());
                    executeWithTimeout(informational, payload, user, automation.getId(), automation.getName());
                }

                // Revert active branches using c1-negative actions if any branch was active
                boolean anyWasActive = automation.getConditions().stream()
                        .filter(Automation.Condition::isEnabled)
                        .filter(c -> isGateCondition(c, operatorIds))
                        .anyMatch(c -> {
                            AutomationState s = cache.getBranchState(c.getNodeId());
                            return s == AutomationState.ACTIVE || s == AutomationState.HOLDING;
                        });

                if (anyWasActive) {
                    List<Automation.Action> c1NegActions =
                            resolveC1NegativeActions(automation, triggerConditions);
                    if (!c1NegActions.isEmpty()) {
                        log.info("⏹️ [{}] [{}] trigger condition false, reverting all branches — {}", user,
                                automation.getName(), actionSummary(c1NegActions));
                        executeWithTimeout(c1NegActions, payload, user, automation.getId(), automation.getName())
                                .thenAccept(ok -> {
                                    if (Boolean.TRUE.equals(ok)) {
                                        // Reset all branch states
                                        automation.getConditions().stream()
                                                .filter(Automation.Condition::isEnabled)
                                                .filter(c -> isGateCondition(c, operatorIds))
                                                .forEach(c -> {
                                                    cache.setBranchState(c.getNodeId(), AutomationState.IDLE);
                                                    redisService.delete("RUNNING:" + automation.getId()
                                                            + ":" + c.getNodeId());
                                                });
                                        cache.setLastUpdate(new Date());
                                        redisService.setAutomationCache(cacheKey, cache);
                                        notificationService.sendNotification(automation.getName() + " — condition lost", "info");
                                    }
                                });
                    }
                }

                saveLog(logBuilder.status(AutomationLog.LogStatus.TRIGGER_FALSE)
                        .reason("Trigger condition false"
                                + (anyWasActive ? " — branches reset, c1-negative actions fired" : ""))
                        .build());
                return;  // ← critical: never advance branch states
            }

            // ── Phase 2: resolve gate branches, sorted by priority DESC ───
            List<GateBranch> branches = resolveGateBranches(automation, ctx);

            if (branches.isEmpty()) {
                // No gate conditions — this automation has no branch structure.
                // Fall back to single-state behaviour (positive / negative on root).
                handleNoBranchAutomation(automation, ctx, payload, cache,
                        cacheKey, user, logBuilder, now);
                return;
            }

            // ── Determine this tick's winner ──────────────────────────────
            // Winner = highest-priority branch whose gate is currently TRUE.
            List<GateBranch> trueBranches = branches.stream()
                    .filter(b -> {
                        NodeResult nr = ctx.get(b.gateNodeId());
                        return nr != null && nr.isTrue();
                    })
                    .toList(); // already sorted DESC by priority from resolveGateBranches()

            GateBranch winner = trueBranches.isEmpty() ? null : trueBranches.get(0);

            // ── Process each branch atomically in the same tick ───────────
            // Order: first revert all branches that should stop,
            //        then promote the winner if it was IDLE.
            // This gives atomic handoff (no gap between scene changes).

            List<CompletableFuture<Void>> tickFutures = new ArrayList<>();
            boolean anyBranchWasOrIsActive = false;

            for (GateBranch branch : branches) {
                boolean gateTrue = trueBranches.contains(branch);
                boolean isWinner = branch == winner;
                AutomationState branchState = cache.getBranchState(branch.gateNodeId());

                if (branchState == AutomationState.ACTIVE
                        || branchState == AutomationState.HOLDING) {

                    if (!gateTrue || !isWinner) {
                        // ── Revert: gate turned false OR outprioritised ───
                        String reason = !gateTrue
                                ? "Condition no longer met (" + describeBranch(branch) + ")"
                                : "Overridden by '" + describeBranch(winner) + "'";
                        String revertMsg = !gateTrue
                                ? "⏹️ " + automation.getName() + " — " + describeBranch(branch) + " ended"
                                : "⏹️ " + automation.getName() + " — " + describeBranch(branch) + " overridden by " + describeBranch(winner);

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
                                                // Clear duration timers for this branch
                                                redisService.delete("RUNNING:" + automation.getId()
                                                        + ":" + fb.gateNodeId());
                                                notificationService.sendNotification(automation.getName() + " — " + reason, "info");
                                                log.info("🔁 [{}] [{}] '{}' → IDLE — {}", user,
                                                        automation.getName(), describeBranch(fb), reason);
                                            }
                                        });
                        tickFutures.add(revertFuture);
                    } else {
                        anyBranchWasOrIsActive = true; // still running
                    }

                } else if (branchState == AutomationState.IDLE) {

                    if (gateTrue && isWinner) {
                        // ── Interval cooldown check ───────────────────────
                        Automation.Condition gc = branch.gateCondition();
                        if ("interval".equals(gc.getScheduleType())
                                && gc.getIntervalMinutes() > 0
                                && cache.getPreviousExecutionTime() != null) {

                            long secondsSinceLast =
                                    (now.getTime() - cache.getPreviousExecutionTime().getTime()) / 1000;
                            long intervalSec = gc.getIntervalMinutes() * 60L;
                            if (secondsSinceLast < intervalSec) {
                                log.debug("⏳ [{}] [{}] '{}' — interval cooldown, {}s until next run", user,
                                        automation.getName(), describeBranch(branch),
                                        intervalSec - secondsSinceLast);
                                continue;
                            }
                        }
                        // ── Trigger: gate true + winner ───────────────────
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

                                            // Only go ACTIVE/HOLDING if there's something to revert to

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
                                            notificationService.sendNotification(automation.getName() + " triggered", "success");
                                        });
                        tickFutures.add(triggerFuture);
                        anyBranchWasOrIsActive = true;

                    } else if (gateTrue || !isWinner) {
                        // ── Suppressed by higher-priority branch ──────────
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
                                        + " has higher priority (" + (winner != null ? winner.priority() : "?") + ")")
                                .build());
                    }
                    // IDLE + gate false → quiet path, nothing to do
                }

                // ── HOLDING: check if duration timer expired ──────────────
                if (branchState == AutomationState.HOLDING) {
                    String runKey = "RUNNING:" + automation.getId() + ":" + branch.gateNodeId();
                    if (!redisService.exists(runKey)) {
                        // Timer expired → revert
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
                                                notificationService.sendNotification(automation.getName() + " — " + describeBranch(fb) + " timer expired", "info");
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

            // ── Phase 3: c1 true but NO branch fired or active ────────────
            // This is the "time window not matched" fallback.
            if (!anyBranchWasOrIsActive && winner == null) {
                List<Automation.Action> c1Fallback =
                        resolveC1FallbackActions(automation, ctx, triggerConditions);
                if (!c1Fallback.isEmpty()) {
                    log.info("ℹ️ [{}] [{}] trigger met but no time window matched — firing fallback", user,
                            automation.getName());
                    executeWithTimeout(c1Fallback, payload, user, automation.getId(), automation.getName());
                    saveLog(logBuilder
                            .status(AutomationLog.LogStatus.TRIGGER_FALSE)
                            .reason("c1 true, no gate branch matched — c1 fallback fired").build());
                } else {
                    saveLog(logBuilder.status(AutomationLog.LogStatus.NOT_MET)
                            .reason("c1 true but no gate branch matched and no fallback actions").build());
                }
                return;
            }
            List<AutomationLog.ConditionResult> conditionResults =
                    buildConditionResults(automation, ctx, payload);
            logBuilder.conditionResults(conditionResults).payload(payload);
            // ── Wait for all this-tick futures then save a single log ─────
            boolean finalAnyBranchWasOrIsActive = anyBranchWasOrIsActive;
            CompletableFuture.allOf(tickFutures.toArray(new CompletableFuture[0]))
                    .thenRun(() -> saveLog(logBuilder
                            .status(finalAnyBranchWasOrIsActive
                                    ? AutomationLog.LogStatus.TRIGGERED
                                    : AutomationLog.LogStatus.SKIPPED)
                            .reason("Branch evaluation complete").build()));

        } catch (Exception e) {
            log.error("❌ Error in automation {}: {}", automation.getName(), e.getMessage(), e);
            notificationService.sendNotification(automation.getName() + " error", "error");
            saveLog(logBuilder.status(AutomationLog.LogStatus.ERROR)
                    .reason("Execution failed: " + e.getMessage()).build());
        } finally {
            releaseLock(lockKey, lockValue);
        }
    }

    /**
     * Fallback path for automations that have NO gate conditions at all
     * (pure trigger → positive/negative, no operators needed).
     * Preserves the original single-state machine behaviour.
     */
    private void handleNoBranchAutomation(Automation automation,
                                          ExecutionContext ctx,
                                          Map<String, Object> payload,
                                          AutomationCache cache,
                                          String cacheKey,
                                          String user,
                                          AutomationLog.AutomationLogBuilder logBuilder,
                                          Date now) {
        // Use the root result directly
        NodeResult root = findRootResult(automation, ctx);
        boolean conditionNow = root != null && root.isTrue();

        AutomationState currentState = resolveState(cache.getState());

        List<Automation.Action> positiveActions = resolveActionsForGroup(automation, ctx, "positive");
        List<Automation.Action> negativeActions = resolveActionsForGroup(automation, ctx, "negative");

        if (currentState == AutomationState.IDLE && conditionNow) {
            final AutomationCache fc = cache;

            // Detect stateless automations: no negative actions defined means
            // this is a fire-and-forget event (knock, button press, etc.)
            // Don't advance to ACTIVE — stay IDLE so next event fires again.
            boolean hasNegativeActions = !negativeActions.isEmpty();

            executeWithTimeout(positiveActions, payload, user, automation.getId(), automation.getName())
                    .thenAccept(ok -> {
                        if (!Boolean.TRUE.equals(ok)) return;
                        if (hasNegativeActions) {
                            // Stateful — advance to ACTIVE, wait for revert
                            fc.setState(AutomationState.ACTIVE);
                        }
                        // Stateless — stay IDLE, ready for next event
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
                        notificationService.sendNotification(automation.getName() + " — condition cleared", "info");
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


    // ═════════════════════════════════════════════════════════════════════
    // GRAPH EVALUATION  (unchanged logic, now also stores wasActive per-branch)
    // ═════════════════════════════════════════════════════════════════════

    public NodeResult evaluate(Automation automation,
                               Map<String, Object> payload,
                               ExecutionContext ctx,
                               AutomationCache cache) {

        List<Automation.Condition> conditions = automation.getConditions() == null
                ? List.of() : automation.getConditions();
        List<Automation.Operator> operators = automation.getOperators() == null
                ? List.of() : automation.getOperators();

        Set<String> operatorIds = operators.stream()
                .map(Automation.Operator::getNodeId)
                .collect(Collectors.toSet());

        List<Automation.Condition> triggerConditions = new ArrayList<>();
        List<Automation.Condition> gateConditions = new ArrayList<>();

        for (Automation.Condition c : conditions) {
            if (!c.isEnabled()) continue;
            (isGateCondition(c, operatorIds) ? gateConditions : triggerConditions).add(c);
        }

        // Phase 1 — trigger conditions
        for (Automation.Condition c : triggerConditions) {
            // wasActive for hysteresis: a trigger condition has no per-branch state,
            // so we use the top-level cache state.
            boolean wasActive = cache != null
                    && (cache.getState() == AutomationState.ACTIVE
                    || cache.getState() == AutomationState.HOLDING);
            boolean result = evaluateCondition(automation, c, payload, wasActive);
            NodeResult nr = new NodeResult(c.getNodeId(), result);
            nr.getContributors().add(c.getNodeId());
            ctx.put(nr);
        }

        // Phase 1b — operators
        for (Automation.Operator op : operators) {
            List<NodeResult> inputs = op.getPreviousNodeRef().stream()
                    .map(ref -> ctx.get(ref.getNodeId()))
                    .filter(Objects::nonNull)
                    .toList();

            boolean result;
            Set<String> contributors = new HashSet<>();

            if (inputs.isEmpty()) {
                log.warn("⚠️ Operator '{}' has no resolved inputs — defaulting false", op.getNodeId());
                result = false;
            } else if ("OR".equalsIgnoreCase(op.getLogicType())) {
                result = inputs.stream().anyMatch(NodeResult::isTrue);
                inputs.stream().filter(NodeResult::isTrue)
                        .forEach(n -> contributors.addAll(n.getContributors()));
            } else {
                result = inputs.stream().allMatch(NodeResult::isTrue);
                inputs.forEach(n -> contributors.addAll(n.getContributors()));
            }

            NodeResult opResult = new NodeResult(op.getNodeId(), result);
            opResult.getContributors().addAll(contributors);
            ctx.put(opResult);
        }

        // Phase 2 — gate conditions
        for (Automation.Condition c : gateConditions) {
            // wasActive for hysteresis: use this branch's state from cache
            boolean wasActive = cache != null
                    && (cache.getBranchState(c.getNodeId()) == AutomationState.ACTIVE
                    || cache.getBranchState(c.getNodeId()) == AutomationState.HOLDING);

            boolean parentTrue = c.getPreviousNodeRef().stream()
                    .map(ref -> ctx.get(ref.getNodeId()))
                    .filter(Objects::nonNull)
                    .anyMatch(NodeResult::isTrue);

            boolean result = parentTrue && evaluateCondition(automation, c, payload, wasActive);
            NodeResult nr = new NodeResult(c.getNodeId(), result);
            if (result) nr.getContributors().add(c.getNodeId());
            ctx.put(nr);
        }

        return findRootResult(automation, ctx);
    }

    /**
     * Backward-compatible overload for callers that don't have a cache reference.
     */
    public NodeResult evaluate(Automation automation,
                               Map<String, Object> payload,
                               ExecutionContext ctx,
                               boolean wasActive) {
        // Build a minimal cache stub so the new overload still works
        AutomationCache stub = AutomationCache.builder()
                .state(wasActive ? AutomationState.ACTIVE : AutomationState.IDLE)
                .build();
        return evaluate(automation, payload, ctx, stub);
    }


    // ═════════════════════════════════════════════════════════════════════
    // BRANCH RESOLUTION HELPERS
    // ═════════════════════════════════════════════════════════════════════

    /**
     * Resolves all gate branches for this automation, sorted by operator priority DESC.
     * Each branch is: operator → gate condition → positive/negative actions.
     */
    private List<GateBranch> resolveGateBranches(Automation automation, ExecutionContext ctx) {
        if (automation.getOperators() == null || automation.getOperators().isEmpty())
            return List.of();

        Set<String> operatorIds = automation.getOperators().stream()
                .map(Automation.Operator::getNodeId)
                .collect(Collectors.toSet());

        return automation.getConditions().stream()
                .filter(Automation.Condition::isEnabled)
                .filter(c -> isGateCondition(c, operatorIds))
                .map(gateCondition -> {
                    // Find the operator this gate is downstream of
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
                .sorted(Comparator.comparingInt(GateBranch::priority).reversed()) // DESC
                .toList();
    }

    /**
     * Actions tied to a specific gate condition node and group (positive/negative).
     */
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
                        return false; // gate-targeted actions must have a ref
                    return a.getPreviousNodeRef().stream().anyMatch(ref -> {
                        if (!ref.getNodeId().equals(nodeId)) return false;
                        String handle = ref.getHandle();
                        if (handle != null && handle.contains("cond-negative"))
                            return falseNodes.contains(ref.getNodeId());
                        return trueNodes.contains(ref.getNodeId())
                                || (handle == null); // no handle = include always
                    });
                })
                .sorted(Comparator.comparingInt(a -> a.getOrder() != 0 ? a.getOrder() : Integer.MAX_VALUE))
                .toList();
    }

    /**
     * "Informational" actions: conditionGroup == "informational".
     * Fired when c1 is false — no state machine involvement.
     */
    private List<Automation.Action> resolveInformationalActions(
            Automation automation, ExecutionContext ctx) {
        return automation.getActions().stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsEnabled()))
                .filter(a -> "informational".equalsIgnoreCase(a.getConditionGroup()))
                .sorted(Comparator.comparingInt(a -> a.getOrder() != 0 ? a.getOrder() : Integer.MAX_VALUE))
                .toList();
    }

    /**
     * c1-fallback actions for Phase 3: c1 is TRUE but no gate branch matched
     * (e.g. lux in range but current time is outside all schedule windows).
     * <p>
     * Picks up "negative" actions whose previousNodeRef points to a TRIGGER
     * condition node WITH a cond-negative handle.  These are intended as
     * "nothing matched" fallback actions.
     * <p>
     * NOTE: do NOT confuse with resolveC1NegativeActions() which fires when
     * c1 itself turns false and active branches need to be reverted.
     */
    private List<Automation.Action> resolveC1FallbackActions(
            Automation automation,
            ExecutionContext ctx,
            List<Automation.Condition> triggerConditions) {

        Set<String> triggerNodeIds = triggerConditions.stream()
                .map(Automation.Condition::getNodeId)
                .collect(Collectors.toSet());

        return automation.getActions().stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsEnabled()))
                .filter(a -> "negative".equalsIgnoreCase(a.getConditionGroup()))
                .filter(a -> a.getPreviousNodeRef() != null
                        && a.getPreviousNodeRef().stream().anyMatch(ref ->
                        triggerNodeIds.contains(ref.getNodeId())
                                && ref.getHandle() != null
                                && ref.getHandle().contains("cond-negative")))
                .sorted(Comparator.comparingInt(a -> a.getOrder() != 0 ? a.getOrder() : Integer.MAX_VALUE))
                .toList();
    }

    /**
     * c1-negative revert actions: fired when c1 turns FALSE and at least one
     * branch was ACTIVE.
     * <p>
     * These are "negative" actions whose previousNodeRef points to a TRIGGER
     * condition node — meaning "lux left the valid range, dim everything".
     * Same node-ref filter as resolveC1FallbackActions but semantically fires
     * on c1=false rather than c1=true-no-branch.
     */
    private List<Automation.Action> resolveC1NegativeActions(
            Automation automation,
            List<Automation.Condition> triggerConditions) {

        Set<String> triggerNodeIds = triggerConditions.stream()
                .map(Automation.Condition::getNodeId)
                .collect(Collectors.toSet());

        return automation.getActions().stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsEnabled()))
                .filter(a -> "negative".equalsIgnoreCase(a.getConditionGroup()))
                .filter(a -> a.getPreviousNodeRef() != null
                        && a.getPreviousNodeRef().stream().anyMatch(ref ->
                        triggerNodeIds.contains(ref.getNodeId())))
                .sorted(Comparator.comparingInt(a -> a.getOrder() != 0 ? a.getOrder() : Integer.MAX_VALUE))
                .toList();
    }

    /**
     * Backward-compatible resolveActions — used by handleNoBranchAutomation.
     */
    public List<Automation.Action> resolveActionsForGroup(
            Automation automation, ExecutionContext ctx, String group) {

        Set<String> trueNodes = ctx.getTrueNodes();
        Set<String> falseNodes = ctx.getFalseNodes();

        return automation.getActions().stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsEnabled()))
                .filter(a -> group.equalsIgnoreCase(a.getConditionGroup()))
                .filter(a -> {
                    if (a.getPreviousNodeRef() == null || a.getPreviousNodeRef().isEmpty())
                        return true; // global action
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

    private boolean isGateCondition(Automation.Condition c, Set<String> operatorIds) {
        return c.getPreviousNodeRef() != null &&
                c.getPreviousNodeRef().stream()
                        .anyMatch(ref -> operatorIds.contains(ref.getNodeId()));
    }


    // ═════════════════════════════════════════════════════════════════════
    // CONDITION EVALUATION  (unchanged)
    // ═════════════════════════════════════════════════════════════════════

    private boolean evaluateCondition(Automation automation, Automation.Condition condition,
                                      Map<String, Object> payload, boolean wasActive) {
        if ("scheduled".equals(condition.getCondition()))
            return isCurrentTimeWithDailyTracking(automation, condition);

        String key = condition.getTriggerKey();
        if (key == null || !payload.containsKey(key)) return false;

        String value = payload.get(key).toString();

        if (!value.matches("-?\\d+(\\.\\d+)?"))
            return value.equals(condition.getValue());

        double v = Double.parseDouble(value);
        double buffer = 5.0;

        if (Boolean.TRUE.equals(condition.getIsExact()))
            return condition.getValue().equals(value);

        return switch (condition.getCondition()) {
            case "equal" -> condition.getValue().equals(value);
            case "above" -> wasActive
                    ? v > (Double.parseDouble(condition.getValue()) - buffer)
                    : v > Double.parseDouble(condition.getValue());

            case "below" -> wasActive
                    ? v < (Double.parseDouble(condition.getValue()) + buffer)
                    : v < Double.parseDouble(condition.getValue());

            case "range" -> {
                double above = Double.parseDouble(condition.getAbove());
                double below = Double.parseDouble(condition.getBelow());
                yield wasActive
                        ? v > (above - buffer) && v < (below + buffer)
                        : v > above && v < below;
            }
            default -> false;
        };
    }


    // ═════════════════════════════════════════════════════════════════════
    // ROOT RESOLUTION
    // ═════════════════════════════════════════════════════════════════════

    private NodeResult findRootResult(Automation automation, ExecutionContext ctx) {
        List<Automation.Operator> operators = automation.getOperators() == null
                ? List.of() : automation.getOperators();

        Set<String> operatorIds = operators.stream()
                .map(Automation.Operator::getNodeId)
                .collect(Collectors.toSet());

        Set<String> triggerNodeIds = automation.getConditions() == null ? Set.of() :
                automation.getConditions().stream()
                .filter(Automation.Condition::isEnabled)
                .filter(c -> !isGateCondition(c, operatorIds))
                .map(Automation.Condition::getNodeId)
                .collect(Collectors.toSet());

        List<Automation.Condition> triggerConditions = automation.getConditions() == null
                ? List.of()
                : automation.getConditions().stream()
                  .filter(Automation.Condition::isEnabled)
                  .filter(c -> !isGateCondition(c, operatorIds))
                  .toList();

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
                    .orElseGet(() -> ctx.get(operators.get(operators.size() - 1).getNodeId()));
        }

        if (triggerConditions.size() > 1) {
            boolean allTrue = triggerConditions.stream()
                    .map(c -> ctx.get(c.getNodeId()))
                    .filter(Objects::nonNull)
                    .allMatch(NodeResult::isTrue);
            NodeResult synthetic = new NodeResult("root:implicit_and", allTrue);
            ctx.put(synthetic);
            return synthetic;
        }

        if (!triggerConditions.isEmpty()) {
            NodeResult nr = ctx.get(triggerConditions.getFirst().getNodeId());
            if (nr != null) return nr;
        }

        return new NodeResult("root:empty", false);
    }


    // ═════════════════════════════════════════════════════════════════════
    // SCHEDULE EVALUATION  (unchanged)
    // ═════════════════════════════════════════════════════════════════════

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
            if (!condition.getDays().contains("Everyday") && !condition.getDays().contains(dow))
                return false;
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
            return Math.abs(ChronoUnit.MINUTES.between(adjusted, current)) <= 3
                    && checkAndSetDailyLock(automation, nowZdt);
        }

        if ("interval".equals(scheduleType)) {
            String intervalKey = "INTERVAL:" + automation.getId() + ":" + condition.getNodeId();
            String runKey = "RUNNING:" + automation.getId() + ":" + condition.getNodeId();
            if (redisService.exists(runKey)) return true;
            if (!redisService.exists(intervalKey)) {
                redisService.setWithExpiry(intervalKey, "run",
                        condition.getIntervalMinutes() * 60L);
                return true;
            }
            return false;
        }

        // "at" / exact time
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
                    "https://api.sunrise-sunset.org/json?lat=" + LOCATION_LAT
                            + "&lng=" + LOCATION_LONG + "&formatted=0", Map.class);
            if (response == null || !response.containsKey("results")) return null;

            @SuppressWarnings("unchecked")
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
    // ACTION EXECUTION  (unchanged)
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
                                                              String automationName
    ) {
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
                                     Map<String, Object> value, String automationId, String automationName) {
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

            // Register for ack tracking — non-blocking
            String deviceName = resolveDeviceLabel(action.getDeviceId(), action.getName());
            deliveryTracker.register(correlationId, automationId, automationName,
                    action.getDeviceId(), deviceName, trackedPayload);
        }
    }


    // ═════════════════════════════════════════════════════════════════════
    // CONDITION RESULT BUILDER  (updated with operatorNodeId + suppressedBy)
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

            // Find parent operator for gate conditions
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

        // Operator nodes
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
        // Redis GET is atomic — no lock needed for a read.
        // The withLock pattern here added 2 Redis round-trips (lock acquire + release)
        // on every event with no safety benefit since reads don't need mutual exclusion.
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
    // DEVICE / MQTT HELPERS  (unchanged)
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
            notificationService.sendNotification(
                    "Reboot failed: " + device.getName(), "error");
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

    private LocalTime parseTime(String timeText) {
        if (timeText == null || timeText.isBlank()) return null;
        try {
            return LocalTime.parse(timeText.trim(),
                    DateTimeFormatter.ofPattern("HH:mm:ss"));
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

    // =========================================================================
    // HUMAN-READABLE MESSAGE HELPERS
    // =========================================================================

    /**
     * Short label describing what a gate branch represents.
     * <p>
     * Scheduled examples:
     * "Every 30 min (active for 20 min)"
     * "Time window 13:05-01:00"
     * "Sunrise +10 min"
     * "At 02:20"
     * <p>
     * Sensor/data examples:
     * "percent < 65"
     * "range in 5-600"
     */
    private String describeBranch(GateBranch branch) {
        return describeCondition(branch.gateCondition());
    }

    private String describeCondition(Automation.Condition c) {
        if ("scheduled".equals(c.getCondition())) {
            String schedType = c.getScheduleType() != null ? c.getScheduleType() : "at";
            return switch (schedType) {
                case "range" -> "Time window " + fmtTime(c.getFromTime()) + "-" + fmtTime(c.getToTime());
                case "solar" -> capitalize(c.getSolarType())
                        + (c.getOffsetMinutes() != 0 ? " +" + c.getOffsetMinutes() + " min" : "");
                case "interval" -> "Every " + c.getIntervalMinutes() + " min"
                        + (c.getDurationMinutes() > 0
                        ? " (active for " + c.getDurationMinutes() + " min)" : "");
                default -> "At " + fmtTime(c.getTime());
            };
        }
        String key = c.getTriggerKey() != null ? c.getTriggerKey() : "value";
        return switch (c.getCondition()) {
            case "above" -> key + " > " + c.getValue();
            case "below" -> key + " < " + c.getValue();
            case "range" -> key + " in " + c.getAbove() + "-" + c.getBelow();
            default -> key + " = " + c.getValue();
        };
    }

    /**
     * Full notification string for a triggered branch.
     * <p>
     * Example:
     * "[Light On] Time window 13:05-01:00 -> Monitor Light preset=8, Light Strip preset=3"
     */
    private String describeTriggered(String automationName, GateBranch fb) {
        String branchDesc = describeCondition(fb.gateCondition());
        String actionsDesc = actionSummary(fb.positiveActions());
        return "[" + automationName + "] " + branchDesc + " -> " + actionsDesc;
    }

    /**
     * Compact summary of an action list.
     * <p>
     * Example: "Monitor Light preset=8, Light Strip bright=100"
     */
    String actionSummary(List<Automation.Action> actions) {
        if (actions == null || actions.isEmpty()) return "no actions";
        return actions.stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsEnabled()))
                .map(a -> resolveDeviceLabel(a.getDeviceId(), a.getName())
                        + " " + a.getKey() + "=" + a.getData())
                .collect(Collectors.joining(", "));
    }

    /**
     * Returns device name from MainService, falling back to the action name
     * so raw device IDs never appear in user-facing messages.
     */
    private String resolveDeviceLabel(String deviceId, String actionName) {
        try {
            Device d = mainService.getDevice(deviceId);
            if (d != null && d.getName() != null && !d.getName().isBlank())
                return d.getName();
        } catch (Exception ignored) {
        }
        return (actionName != null && !actionName.isBlank()) ? actionName : deviceId;
    }

    /**
     * Strips seconds: "13:05:00" -> "13:05", handles AM/PM strings too.
     */
    private String fmtTime(String raw) {
        if (raw == null || raw.isBlank()) return "?";
        return raw.replaceAll(":\\d{2}(\\s*[AaPp][Mm])?$", "$1").trim();
    }

    private String capitalize(String s) {
        if (s == null || s.isBlank()) return "";
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
    }

}