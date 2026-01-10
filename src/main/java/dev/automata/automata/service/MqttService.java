package dev.automata.automata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.automata.automata.dto.LiveEvent;
import dev.automata.automata.model.Status;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

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
            System.err.println("Device Id not found");;
        }
        actionService.handleAction(deviceId, payload, "", "device");
    }

    @ServiceActivator(inputChannel = "sendLiveData")
    public void sendLiveData(Map<String, Object> payload) {
        String deviceId = payload.get("device_id").toString();
        if (deviceId.isEmpty() || deviceId.equals("null")) {
            System.err.println("No device found");
        }
        var event  = new LiveEvent();
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
        System.out.println("✅ Status: " + payload);
    }

    @ServiceActivator(inputChannel = "sysData")
    public void sysData(Object payload) {
        System.out.println("✅ sysData: " + payload);
    }
}
