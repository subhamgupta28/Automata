package dev.automata.automata.service;

import dev.automata.automata.automation_engine.AutomationService;
import dev.automata.automata.automation_extras.ActionDeliveryTracker;
import dev.automata.automata.dto.LiveEvent;
import dev.automata.automata.dto.WledResponse;
import dev.automata.automata.modules.Wled;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
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
    private final HomeRoutingService homeRoutingService;          // ← inject

    @ServiceActivator(inputChannel = "sendData")
    public void sendData(Map<String, Object> payload) {
//        System.out.println("📡 Data: " + payload);
        String deviceId = payload.get("device_id").toString();
        if (deviceId.isEmpty() || deviceId.equals("null")) {
            log.error("sendData: missing device_id");
            return;
        }
        payload.put("last_seen", System.currentTimeMillis());
        if (payload.size() > 1)
            mainService.saveData(deviceId, payload);

        var map = new HashMap<String, Object>();
        map.put("deviceId", deviceId);
        map.put("data", payload);

//        messagingTemplate.convertAndSend("/topic/data", map);

        homeRoutingService.routeToHome(deviceId, "data", getStringObjectMap(payload, deviceId));
        recordingRoutingService.route(deviceId, payload);
    }

    @ServiceActivator(inputChannel = "ackAction")
    public void ackAction(Map<String, Object> payload) {
        System.err.println("📡 Ack Action: " + payload);
        System.err.println("got action message: " + payload);
        String deviceId = payload.get("device_id").toString();
        if (deviceId.isEmpty() || deviceId.equals("null")) {
            System.err.println("Device Id not found");
        }
        actionService.ackAction(deviceId, payload);
    }

    @ServiceActivator(inputChannel = "action")
    public void action(Map<String, Object> payload) {
        log.info("📡 Action: {}", payload);
        log.info("got action message: {}", payload);
        String deviceId = payload.getOrDefault("device_id", "").toString();
        if (deviceId.equals("null") || deviceId.isBlank()) {
            log.error("Device Id not found");
        }
        actionService.handleAction(deviceId, payload, "", "device", "SYSTEM");
    }

    @ServiceActivator(inputChannel = "sendLiveData")
    public void sendLiveData(Map<String, Object> payload) {
        String deviceId = payload.get("device_id").toString();
//        System.err.println("sendLiveData: " + payload);
        if (deviceId.isEmpty() || deviceId.equals("null")) {
            log.error("sendLiveData: missing device_id");
            return;
        }
        payload.put("last_seen", new Date());
        var event = new LiveEvent();
        event.setPayload(payload);
        publisher.publishEvent(event);
//        messagingTemplate.convertAndSend("/topic/data", getStringObjectMap(payload, deviceId));
        // hot path — one local ConcurrentHashMap lookup via DeviceHomeCache,
        // envelope already built, no extra allocation beyond HashMap(2)
        homeRoutingService.routeToHome(deviceId, "data", getStringObjectMap(payload, deviceId));
        recordingRoutingService.route(deviceId, payload);
    }

    private Map<String, Object> getStringObjectMap(@Payload Map<String, Object> payload, String deviceId) {
        var map = new HashMap<String, Object>();
        map.put("deviceId", deviceId);
        map.put("data", payload);
        return map;
    }

    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handleAck(Map<String, Object> payload) {
//        System.out.println("✅ Status: " + payload);
    }

    @ServiceActivator(inputChannel = "sysData")
    public void sysData(Message<?> message) {
//        String topic = (String) message.getHeaders().get("mqtt_receivedTopic");
//        String time = message.getPayload().toString();
//        if (topic == null) return;
//        String[] macAddresses = topic.split("-");
//        var status = Status.INACTIVE;
//        if (topic.contains("/connected")) {
//            status = Status.ONLINE;
//        } else if (topic.contains("/disconnected")) {
//            status = Status.OFFLINE;
//        }
//        var address = macAddresses[macAddresses.length - 1];
//        var res = mainService.setStatusOfDeviceByMacAddress(address, status);
    }

    @ServiceActivator(inputChannel = "wledChannel")
    public void handleWled(Message<?> message) {
        String topic = (String) message.getHeaders().get("mqtt_receivedTopic");
//        log.info("WLED message on topic [{}]", topic);

        if (topic == null || !topic.startsWith("automata-wled/")) {
            log.warn("handleWled received unexpected topic: {}", topic);
            return;
        }

        // "automata-wled/" is 14 characters
        String deviceName = topic.substring(14);
        if (deviceName.isEmpty()) {
            log.warn("handleWled: topic has no device segment: {}", topic);
            return;
        }

        String rawPayload = message.getPayload().toString(); // raw XML — untouched

        if (deviceName.endsWith("/v")) {
            deviceName = deviceName.replace("/v", "").replaceAll("/", "");
            var device = mainService.getDeviceByCategory(deviceName);

            if (device == null) {
                log.warn("handleWled: no device found for category [{}]", deviceName);
                return;
            }

            var wled = new Wled(null, device);

            WledResponse response = wled.parseWledXml(rawPayload);
            var data = wled.convertToMap(response, device.getId());

            mainService.saveData(device.getId(), data);
            Map<String, Object> payload = new HashMap<>();
            payload.put("deviceId", device.getId());
            payload.put("data", data);
            homeRoutingService.routeToHome(device.getId(), "data", payload);
            deliveryTracker.confirmWled(device.getId(), deviceName);
            log.info("WLED processed for [{}]: {}", device.getName(), response);
        }
    }
}
