package dev.automata.automata.v2;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.automata.automata.model.AutomationLog;
import dev.automata.automata.model.DeviceActionState;
import dev.automata.automata.modules.Spotify;
import dev.automata.automata.modules.SpotifyService;
import dev.automata.automata.modules.Wled;
import dev.automata.automata.repository.DeviceActionStateRepository;
import dev.automata.automata.repository.DeviceRepository;
import dev.automata.automata.service.ActionDeliveryTracker;
import dev.automata.automata.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.*;

/**
 * Dispatches compiled actions to devices.
 * <p>
 * Change vs previous version:
 * - Injects AutomationLivePublisher and calls publishActionFired() after every
 * successful (or failed) single-action dispatch so the inspector's Actions
 * Fired tab receives live events on /topic/automation.{id}.actions.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActionDispatcher {

    private final SimpMessagingTemplate messagingTemplate;
    private final MessageChannel mqttOutboundChannel;
    private final DeviceRepository deviceRepository;
    private final DeviceActionStateRepository deviceActionStateRepository;
    private final NotificationService notificationService;
    private final ActionDeliveryTracker deliveryTracker;
    private final ObjectMapper objectMapper;
    private final Executor actionDispatchExecutor;
    private final ScheduledExecutorService actionDelayScheduler;
    private final AutomationLogStream logStream;
    private final AutomationLivePublisher livePublisher;   // ← ADDED
    private final SpotifyService spotifyService;

    private static final long ACTION_TIMEOUT_SECONDS = 30;


    // ─────────────────────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────────────────────

    public CompletableFuture<Boolean> dispatch(List<ExecutionPlan.CompiledAction> actions,
                                               Map<String, Object> payload,
                                               String user,
                                               String automationId,
                                               String automationName,
                                               String traceId, String homeId) {
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

    public void notifyTriggered(String automationName, String homeId) {
        notificationService.sendNotification(automationName + " triggered", "success", homeId);
    }

    public void notifyReverted(String automationName, String branchDesc, String homeId) {
        notificationService.sendNotification(
                automationName + " — " + branchDesc + " ended", "info", homeId);
    }

    public void notifyError(String automationName, String homeId) {
        notificationService.sendNotification(automationName + " error", "error", homeId);
    }


    // ─────────────────────────────────────────────────────────────────────
    // CHAIN BUILDER
    // ─────────────────────────────────────────────────────────────────────

    private CompletableFuture<Boolean> buildChain(List<ExecutionPlan.CompiledAction> actions,
                                                  Map<String, Object> payload,
                                                  String user,
                                                  String automationId,
                                                  String automationName,
                                                  String traceId, String homeId) {
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

    private void dispatchSingle(ExecutionPlan.CompiledAction action,
                                Map<String, Object> livePayload,
                                String user,
                                String automationId,
                                String automationName,
                                String traceId, String homeId) {
        Object parsedData = parseData(action.getData());
        Map<String, Object> payload = Map.of(action.getKey(), parsedData, "key", action.getKey());

        if ("alert".equals(action.getKey())) {
            notificationService.sendAlert(
                    "Alert: " + action.getData().toUpperCase(Locale.ROOT), action.getData(), homeId);
            // Alert has no device ACK — mark immediately
            logStream.updateDeliveryStatus(
                    traceId, AutomationLog.DeliveryStatus.NOT_APPLICABLE, new Date());
            return;
        }
        if ("app_notify".equals(action.getKey())) {
            notificationService.sendNotify("Automation", action.getData(), "low");
            logStream.updateDeliveryStatus(
                    traceId, AutomationLog.DeliveryStatus.NOT_APPLICABLE, new Date());
            return;
        }
        if ("WLED".equals(action.getDeviceType())) {
            dispatchWled(action.getDeviceId(), new HashMap<>(payload), user, automationId, automationName, traceId);
            // WLED has no ACK path currently — mark NOT_APPLICABLE
            logStream.updateDeliveryStatus(
                    traceId, AutomationLog.DeliveryStatus.NOT_APPLICABLE, new Date());
            return;
        }
        if ("MEDIA".equals(action.getDeviceType())) {
            dispatchSpotify(action.getDeviceId(), new HashMap<>(payload), user, automationId, automationName, traceId);
            logStream.updateDeliveryStatus(
                    traceId, AutomationLog.DeliveryStatus.NOT_APPLICABLE, new Date());
            return;
        }

        // Standard device — correlation tracked via _cid
        String correlationId = UUID.randomUUID().toString();
        Map<String, Object> trackedPayload = new HashMap<>(payload);
        trackedPayload.put("_cid", correlationId);

        deviceActionStateRepository.save(DeviceActionState.builder()
                .user(user).deviceId(action.getDeviceId())
                .timestamp(new Date()).payload(trackedPayload).deviceType("sensor").build());

        sendToDevice(action.getDeviceId(), trackedPayload);

        // Register delivery tracking — now also carries traceId so that when the
        // device ACKs, ActionDeliveryTracker can call logStream.updateDeliveryStatus()
        deliveryTracker.register(correlationId, automationId, automationName,
                action.getDeviceId(), action.getName(), trackedPayload,
                traceId);
    }

    private void dispatchSpotify(String deviceId, HashMap<String, Object> payload, String user, String automationId, String automationName, String traceId) {
        try {
            new Spotify(spotifyService, deviceId).handleAction(payload);
            deliveryTracker.registerWled(
                    deviceId, automationId, automationName, deviceId, payload, traceId);

        } catch (Exception e) {
            log.error("Spotify dispatch error for '{}': {}", deviceId, e.getMessage());
            logStream.updateDeliveryStatus(
                    traceId, AutomationLog.DeliveryStatus.DELIVERY_FAILED, new Date());
        }
    }

    private void sendToDevice(String deviceId, Map<String, Object> payload) {
        messagingTemplate.convertAndSend("/topic/action." + deviceId, payload);
        sendToMqtt("action/" + deviceId, payload);
    }

    private void dispatchWled(String deviceId, Map<String, Object> payload, String user) {
        deviceRepository.findById(deviceId).ifPresent(device -> {
            try {
                new Wled(mqttOutboundChannel, device).handleAction(payload);
            } catch (Exception e) {
                log.error("WLED dispatch error for '{}': {}", deviceId, e.getMessage());
            }
        });
    }

    private void dispatchWled(String deviceId,
                              Map<String, Object> payload,
                              String user,
                              String automationId,
                              String automationName,
                              String traceId) {
        deviceRepository.findById(deviceId).ifPresent(device -> {
            try {
                new Wled(mqttOutboundChannel, device).handleAction(payload);

                // Register for WLED delivery tracking instead of NOT_APPLICABLE.
                // Confirmation arrives via handleWled() when WLED publishes /v.
                deliveryTracker.registerWled(
                        deviceId, automationId, automationName, device.getName(), payload, traceId);

            } catch (Exception e) {
                log.error("WLED dispatch error for '{}': {}", deviceId, e.getMessage());
                // Dispatch itself failed — mark immediately
                logStream.updateDeliveryStatus(
                        traceId, AutomationLog.DeliveryStatus.DELIVERY_FAILED, new Date());
            }
        });
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