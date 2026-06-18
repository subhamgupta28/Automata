package dev.automata.automata.service;

import dev.automata.automata.dto.LiveEvent;
import dev.automata.automata.dto.WledResponse;
import dev.automata.automata.modules.Wled;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MqttService {

    private final MainService mainService;
    private final AutomationService actionService;
    private final ApplicationEventPublisher publisher;
    private final ActionDeliveryTracker deliveryTracker;
    private final RecordingRoutingService recordingRoutingService;
    private final HomeRoutingService homeRoutingService;   // ← NEW, replaces direct ws calls

    // ── sendData ─────────────────────────────────────────────────────────────
    // Topic:     "sendData"
    // Published: ESP32 sensor readings (temperature, humidity, etc.)
    // No topic change needed on device — we resolve homeId here.
    @ServiceActivator(inputChannel = "sendData")
    public void sendData(Map<String, Object> payload) {
        String deviceId = extractDeviceId(payload);
        if (deviceId == null) return;

        payload.put("last_seen", System.currentTimeMillis());
        mainService.saveData(deviceId, payload);

        // Single call — resolves homeId internally, broadcasts to /topic/home/{homeId}/data
        homeRoutingService.routeToHome(deviceId, "data", payload);
        recordingRoutingService.route(deviceId, payload);
    }

    // ── sendLiveData ──────────────────────────────────────────────────────────
    // Topic:     "sendLiveData"
    // Published: high-frequency live sensor stream (charts, gauges)
    @ServiceActivator(inputChannel = "sendLiveData")
    public void sendLiveData(Map<String, Object> payload) {
        String deviceId = extractDeviceId(payload);
        if (deviceId == null) return;

        payload.put("last_seen", new Date());

        // Spring ApplicationEvent — internal use (AutomationService listeners etc.)
        // Nothing changes here, automations don't care about homeId yet
        LiveEvent event = new LiveEvent();
        event.setPayload(payload);
        publisher.publishEvent(event);

        // Route to home-scoped "live" topic — separate from "data" so frontend
        // can subscribe to high-frequency live without getting persisted snapshots
        homeRoutingService.routeToHome(deviceId, "live", payload);
        recordingRoutingService.route(deviceId, payload);
    }

    // ── action ────────────────────────────────────────────────────────────────
    // Topic:     "action"
    // Published: device-initiated actions (button press, trigger, etc.)
    @ServiceActivator(inputChannel = "action")
    public void action(Map<String, Object> payload) {
        String deviceId = extractDeviceId(payload);
        if (deviceId == null) return;

        // Existing logic untouched — actionService handles the business logic
        actionService.handleAction(deviceId, payload, "", "device");

        // Also broadcast to home so the dashboard can show "Device triggered X"
        homeRoutingService.routeToHome(deviceId, "action", payload);
    }

    // ── ackAction ─────────────────────────────────────────────────────────────
    // Topic:     "ackAction"
    // Published: device confirms it executed a command
    @ServiceActivator(inputChannel = "ackAction")
    public void ackAction(Map<String, Object> payload) {
        String deviceId = extractDeviceId(payload);
        if (deviceId == null) return;

        actionService.ackAction(deviceId, payload);

        // Route ack to home so frontend can show confirmation (e.g. "Light turned on ✓")
        homeRoutingService.routeToHome(deviceId, "ack", payload);
    }

    // ── mqttInputChannel (status / misc) ──────────────────────────────────────
    // Topic:     "status" + wled fallback
    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handleAck(Map<String, Object> payload) {
        // Most messages here are internal acks — only route if device_id present
        Object rawId = payload.get("device_id");
        if (rawId != null && !rawId.toString().isBlank()) {
            homeRoutingService.routeToHome(rawId.toString(), "status", payload);
        }
    }

    // ── sysData ───────────────────────────────────────────────────────────────
    // Topic:     "broker/status/#" (commented out in your config, keeping parity)
    @ServiceActivator(inputChannel = "sysData")
    public void sysData(Message<?> message) {
        // Uncomment and adapt if you re-enable broker/status subscription
        // String topic = (String) message.getHeaders().get("mqtt_receivedTopic");
        // Parse MAC, resolve to deviceId, then homeRoutingService.routeEvent(...)
    }

    // ── wledChannel ───────────────────────────────────────────────────────────
    // Topic:     "automata-wled/#"
    // Device name comes from topic header, not from payload.device_id
    @ServiceActivator(inputChannel = "wledChannel")
    public void handleWled(Message<?> message) {
        String deviceName = (String) message.getHeaders().get("device");
        String payloadStr = message.getPayload().toString();
        if (deviceName == null) return;

        if (!deviceName.endsWith("/v")) return;

        deviceName = deviceName.replace("/v", "").replaceAll("/", "");
        var device = mainService.getDeviceByCategory(deviceName);
        if (device == null) return;

        var wled = new Wled(null, device);
        WledResponse response = wled.parseWledXml(payloadStr);
        var data = wled.convertToMap(response, device.getId());
        mainService.saveData(device.getId(), data);

        // Route via homeRoutingService instead of direct convertAndSend
        homeRoutingService.routeToHome(device.getId(), "data", data);

        deliveryTracker.confirmWled(device.getId(), deviceName);
        log.info("WLED Response for [{}] data: [{}]", device.getName(), response);
    }

    // ── Shared helper ─────────────────────────────────────────────────────────
    private String extractDeviceId(Map<String, Object> payload) {
        Object id = payload.get("device_id");
        if (id == null || id.toString().isBlank() || id.toString().equals("null")) {
            log.error("Missing device_id in payload: {}", payload);
            return null;
        }
        return id.toString();
    }
}
