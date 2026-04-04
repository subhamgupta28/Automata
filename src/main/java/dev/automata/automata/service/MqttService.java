package dev.automata.automata.service;

import dev.automata.automata.dto.LiveEvent;
import dev.automata.automata.dto.WledResponse;
import dev.automata.automata.modules.Wled;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class MqttService {


    private final SimpMessagingTemplate messagingTemplate;
    private final MainService mainService;
    private final AutomationService actionService;
    private final ApplicationEventPublisher publisher;


    @ServiceActivator(inputChannel = "sendData")
    public void sendData(Map<String, Object> payload) {
//        System.out.println("📡 Data: " + payload);
        String deviceId = payload.get("device_id").toString();
        if (deviceId.isEmpty() || deviceId.equals("null")) {
            System.err.println("No device found");
        }
        if (payload.size() > 1)
            mainService.saveData(deviceId, payload);
//        var device = mainService.setStatus(deviceId, Status.ONLINE);
        var map = new HashMap<String, Object>();
        map.put("deviceId", deviceId);
        map.put("data", payload);
//        map.put("deviceConfig", device.get("deviceConfig"));
        messagingTemplate.convertAndSend("/topic/data", map);
    }

    @ServiceActivator(inputChannel = "action")
    public void action(Map<String, Object> payload) {
        System.out.println("📡 Action: " + payload);
        System.err.println("got action message: " + payload);
        String deviceId = payload.get("device_id").toString();
        if (deviceId.isEmpty() || deviceId.equals("null")) {
            System.err.println("Device Id not found");
        }
        actionService.handleAction(deviceId, payload, "", "device");
    }

    @ServiceActivator(inputChannel = "sendLiveData")
    public void sendLiveData(Map<String, Object> payload) {
        String deviceId = payload.get("device_id").toString();
//        System.err.println("sendLiveData: " + payload);
        if (deviceId.isEmpty() || deviceId.equals("null")) {
            System.err.println("No device found");
        }
        var event = new LiveEvent();
        event.setPayload(payload);
        publisher.publishEvent(event);
        messagingTemplate.convertAndSend("/topic/data", getStringObjectMap(payload, deviceId));

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

        String deviceName = (String) message.getHeaders().get("device");
        String payload = message.getPayload().toString();
        if (deviceName == null) {
            return;
        }
        if (deviceName.endsWith("/v")) {
            deviceName = deviceName.replace("/v", "");
            deviceName = deviceName.replaceAll("/", "");
            var device = mainService.getDeviceByCategory(deviceName);

            if (device == null)
                return;

            var wled = new Wled(null, device);

            WledResponse response = wled.parseWledXml(payload);
            var data = wled.convertToMap(response, device.getId());
            mainService.saveData(device.getId(), data);
            messagingTemplate.convertAndSend("/topic/data", Map.of("deviceId", device.getId(), "data", data));
            System.err.println("WLED Response for " + device.getName() + " data:" + response);
        }


    }
}
