package dev.automata.automata.automation_engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.automata.automata.automation_extras.ActionDeliveryTracker;
import dev.automata.automata.cache.DeviceMetaCache;
import dev.automata.automata.model.AutomationLog;
import dev.automata.automata.model.DeviceActionState;
import dev.automata.automata.modules.Spotify;
import dev.automata.automata.modules.SpotifyService;
import dev.automata.automata.modules.Wled;
import dev.automata.automata.repository.DeviceActionStateRepository;
import dev.automata.automata.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class ActionDispatcher {

    private final SimpMessagingTemplate messagingTemplate;
    private final MessageChannel mqttOutboundChannel;

    private final DeviceActionStateRepository deviceActionStateRepository;
    private final NotificationService notificationService;
    private final ActionDeliveryTracker deliveryTracker;
    private final ObjectMapper objectMapper;
    private final Executor actionDispatchExecutor;
    private final ScheduledExecutorService actionDelayScheduler;
    private final AutomationLogStream logStream;
    private final AutomationLivePublisher livePublisher;
    private final SpotifyService spotifyService;
    private final DeviceMetaCache deviceMetaCache;

    private static final long ACTION_TIMEOUT_SECONDS = 30;
    private static final long RECORD_TTL_DAYS = 30;


    // ─────────────────────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────────────────────

    public CompletableFuture<Boolean> dispatch(
            List<ExecutionPlan.CompiledAction> actions,
            Map<String, Object> payload,
            String user,
            String automationId,
            String automationName,
            String traceId,
            String homeId
    ) {
        if (actions == null || actions.isEmpty()) {
            // No trackable actions — resolve delivery immediately as NOT_APPLICABLE
            logStream.updateDeliveryStatus(
                    traceId,
                    AutomationLog.DeliveryStatus.NOT_APPLICABLE,
                    new Date());
            return CompletableFuture.completedFuture(true);
        }

        return buildChain(actions, payload, user, automationId, automationName, traceId, homeId)
                .orTimeout(ACTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                    if (cause instanceof TimeoutException)
                        log.error("⏱️ [traceId={}] Action chain timed out for '{}'",
                                traceId, automationName);
                    else
                        log.error("❌ [traceId={}] Action chain error in '{}': {}",
                                traceId, automationName, cause.getMessage(), cause);
                    // Chain failure = delivery failed
                    logStream.updateDeliveryStatus(
                            traceId,
                            AutomationLog.DeliveryStatus.DELIVERY_FAILED,
                            new Date());
                    return false;
                });
    }

    public void dispatchDirect(String deviceId, Map<String, Object> payload) {
        String key = payload.get("key").toString();
        Map<String, Object> map = new HashMap<>();
        map.put(key, payload.get(key));
        map.put("key", key);
        map.put("actionType", "direct");
        sendToDevice(deviceId, map);
    }

    public void notifyTriggered(String automationName, String homeId, String automationId, String reason) {
        String message = reason != null
                ? automationName + " running — " + reason
                : automationName + " is running";
        notificationService.sendNotification(message, "automation", "Automation", homeId, automationId);
    }

    public void notifyReverted(String automationName, String branchDesc, String homeId) {
        notificationService.sendNotification(branchDesc + " ended", "info", "Automation", homeId);
    }

    public void notifyError(String automationName, String homeId) {
        notificationService.sendNotification(
                "Something went wrong while running this automation",
                "error", "Automation", homeId);
    }


    // ─────────────────────────────────────────────────────────────────────
    // CHAIN BUILDER
    // ─────────────────────────────────────────────────────────────────────

    private CompletableFuture<Boolean> buildChain(
            List<ExecutionPlan.CompiledAction> actions,
            Map<String, Object> payload,
            String user,
            String automationId,
            String automationName,
            String traceId,
            String homeId
    ) {
        CompletableFuture<Boolean> chain = CompletableFuture.completedFuture(true);

        for (ExecutionPlan.CompiledAction action : actions) {
            chain = chain.thenCompose(prevOk -> {
                CompletableFuture<Boolean> step = CompletableFuture.supplyAsync(() -> {
                    boolean success = false;
                    try {
                        log.info("▶️ [traceId={}] [{}] Dispatching: {} {}={} (order={})",
                                traceId, automationName, action.getName(),
                                action.getKey(), action.getData(), action.getOrder());
                        dispatchSingle(action, payload, user, automationId, automationName, traceId, homeId);
                        success = true;
                        return true;
                    } catch (Exception e) {
                        log.error("❌ [traceId={}] [{}] Failed dispatch '{}': {}",
                                traceId, automationName, action.getName(), e.getMessage(), e);
                        return false;
                    } finally {
                        // ── Publish live action event regardless of success/failure ──
                        livePublisher.publishActionFired(
                                automationId, automationName, action, success, traceId);
                    }
                }, actionDispatchExecutor);

                if (action.getDelaySeconds() > 0) {
                    return step.thenCompose(ok -> {
                        CompletableFuture<Boolean> delayed = new CompletableFuture<>();
                        actionDelayScheduler.schedule(
                                () -> delayed.complete(ok),
                                action.getDelaySeconds(), TimeUnit.SECONDS);
                        return delayed;
                    });
                }
                return step;
            });
        }
        return chain;
    }


    // ─────────────────────────────────────────────────────────────────────
    // SINGLE ACTION
    // ─────────────────────────────────────────────────────────────────────

    private void dispatchSingle(
            ExecutionPlan.CompiledAction action,
            Map<String, Object> livePayload,
            String user,
            String automationId,
            String automationName,
            String traceId,
            String homeId
    ) {
        Object parsedData = parseData(action.getData());
        Date now = new Date();
        Date expireAt = Date.from(Instant.now().plus(RECORD_TTL_DAYS, ChronoUnit.DAYS));

        // ── alert ──────────────────────────────────────────────────────────
        if ("alert".equals(action.getKey())) {
            notificationService.sendAlert(
                    automationName + " triggered and it's " + action.getData(),
                    action.getData());
            logStream.updateDeliveryStatus(traceId,
                    AutomationLog.DeliveryStatus.NOT_APPLICABLE, now);

            saveRecord(automationId, automationName, traceId, user,
                    "system_alert", action, Map.of(), now, expireAt,
                    DeviceActionState.DispatchOutcome.NOT_APPLICABLE, ""
            );
            return;
        }

        // ── app_notify ─────────────────────────────────────────────────────
        if ("app_notify".equals(action.getKey())) {
            notificationService.sendNotify("Automation", action.getData(), "low");
            logStream.updateDeliveryStatus(traceId,
                    AutomationLog.DeliveryStatus.NOT_APPLICABLE, now);

            saveRecord(automationId, automationName, traceId, user,
                    "system_app_notify", action, Map.of(), now, expireAt,
                    DeviceActionState.DispatchOutcome.NOT_APPLICABLE, ""
            );
            return;
        }

        // ── WLED ───────────────────────────────────────────────────────────
        if ("WLED".equals(action.getDeviceType())) {
            Map<String, Object> wledPayload = new HashMap<>(
                    Map.of(action.getKey(), parsedData, "key", action.getKey()));
            boolean wledOk = dispatchWled(action.getDeviceId(), wledPayload,
                    user, automationId, automationName, traceId, homeId);

            logStream.updateDeliveryStatus(traceId,
                    AutomationLog.DeliveryStatus.NOT_APPLICABLE, now);
            saveRecord(automationId, automationName, traceId, user,
                    "WLED", action, wledPayload, now, expireAt,
                    wledOk ? DeviceActionState.DispatchOutcome.NOT_APPLICABLE
                            : DeviceActionState.DispatchOutcome.DELIVERY_FAILED,
                    wledOk ? null : "WLED dispatch threw exception"
            );
            return;
        }

        // ── MEDIA ──────────────────────────────────────────────────────────
        if ("MEDIA".equals(action.getDeviceType())) {
            Map<String, Object> mediaPayload = new HashMap<>(
                    Map.of(action.getKey(), parsedData, "key", action.getKey()));
            boolean mediaOk = dispatchMedia(action.getDeviceId(), mediaPayload,
                    user, automationId, automationName, traceId, homeId);

            logStream.updateDeliveryStatus(traceId,
                    AutomationLog.DeliveryStatus.NOT_APPLICABLE, now);
            saveRecord(automationId, automationName, traceId, user,
                    "MEDIA", action, mediaPayload, now, expireAt,
                    mediaOk ? DeviceActionState.DispatchOutcome.NOT_APPLICABLE
                            : DeviceActionState.DispatchOutcome.DELIVERY_FAILED,
                    mediaOk ? null : "MEDIA dispatch threw exception"
            );
            return;
        }

        // Standard device — correlation tracked via _cid
        String correlationId = UUID.randomUUID().toString();
        Map<String, Object> trackedPayload = new HashMap<>(
                Map.of(action.getKey(), parsedData, "key", action.getKey()));
        trackedPayload.put("_cid", correlationId);

        // Save record first with PENDING — ActionDeliveryTracker fills ackedAt + ACKED
        // (or DELIVERY_FAILED on timeout) by calling updateDeliveryOutcome() below.

        DeviceActionState saved = saveRecord(automationId, automationName, traceId, user,
                action.getDeviceType(), action, trackedPayload, now, expireAt,
                DeviceActionState.DispatchOutcome.PENDING, ""
        );

        sendToDevice(action.getDeviceId(), trackedPayload);

        // Register with delivery tracker — passes saved record id so the tracker
        // can call updateDeliveryOutcome(id, ACKED, ackedAt) on ACK.
        deliveryTracker.register(
                correlationId,
                automationId,
                automationName,
                action.getDeviceId(),
                action.getName(),
                trackedPayload,
                traceId, saved != null ? saved.getId() : null);  // ← new param
    }


    // ─────────────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────────────

    private DeviceActionState saveRecord(
            String automationId,
            String automationName,
            String traceId,
            String user,
            String deviceType,
            ExecutionPlan.CompiledAction action,
            Map<String, Object> payload,
            Date now, Date expireAt,
            DeviceActionState.DispatchOutcome outcome,
            String errorReason
    ) {
        DeviceActionState record = DeviceActionState.builder()
                .automationId(automationId)
                .automationName(automationName)
                .traceId(traceId)
                .deviceId(action.getDeviceId())
                .deviceName(action.getName())
                .deviceType(deviceType)
                .key(action.getKey())
                .data(action.getData())
                .order(action.getOrder())
                .delaySeconds(action.getDelaySeconds())
                .conditionGroup(action.getConditionGroup())
                .user(user)
                .payload(payload)
                .dispatchedAt(now)
                .outcome(outcome)
                .expireAt(expireAt)
                .errorReason(errorReason)
                .build();
        try {
            return deviceActionStateRepository.save(record);
        } catch (Exception e) {
            log.error("❌ Failed to save DeviceActionState for traceId={}: {}",
                    record.getTraceId(), e.getMessage());
            return null;
        }
    }

    private void sendToDevice(String deviceId, Map<String, Object> payload) {
        messagingTemplate.convertAndSend("/topic/action." + deviceId, Optional.ofNullable(payload));
        sendToMqtt("action/" + deviceId, payload);
    }

    /**
     * Returns true if dispatch succeeded, false if it threw.
     */
    private boolean dispatchWled(String deviceId, Map<String, Object> payload,
                                 String user, String automationId,
                                 String automationName, String traceId, String homeId) {
        try {
            deviceMetaCache.getDevice(deviceId).ifPresent(device -> {
                new Wled(mqttOutboundChannel, device).handleAction(payload);
                deliveryTracker.registerWled(deviceId, automationId,
                        automationName, device.getName(), payload, traceId);
            });
            return true;
        } catch (Exception e) {
            log.error("WLED dispatch error for '{}': {}", deviceId, e.getMessage());
            logStream.updateDeliveryStatus(traceId,
                    AutomationLog.DeliveryStatus.DELIVERY_FAILED, new Date());
            return false;
        }
    }

    /**
     * Returns true if dispatch succeeded, false if it threw.
     */
    private boolean dispatchMedia(String deviceId, Map<String, Object> payload,
                                  String user, String automationId,
                                  String automationName, String traceId, String homeId) {
        try {
            deviceMetaCache.getDevice(deviceId).ifPresent(device -> {
                new Spotify(spotifyService, device.getId()).handleAction(payload);
                deliveryTracker.registerWled(deviceId, automationId,
                        automationName, device.getName(), payload, traceId);
            });
            return true;
        } catch (Exception e) {
            log.error("MEDIA dispatch error for '{}': {}", deviceId, e.getMessage());
            logStream.updateDeliveryStatus(traceId,
                    AutomationLog.DeliveryStatus.DELIVERY_FAILED, new Date());
            return false;
        }
    }

    private void sendToMqtt(String topic, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            mqttOutboundChannel.send(MessageBuilder.withPayload(json)
                    .setHeader("mqtt_topic", topic).build());
        } catch (Exception e) {
            log.error("MQTT send error on '{}': {}", topic, e.getMessage());
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
}