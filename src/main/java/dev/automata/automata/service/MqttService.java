package dev.automata.automata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.automata.automata.dto.LiveEvent;
import lombok.RequiredArgsConstructor;
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

    private final MessageChannel mqttOutboundChannel;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SimpMessagingTemplate messagingTemplate;

    public void sendToTopic(String topic, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            mqttOutboundChannel.send(
                    MessageBuilder.withPayload(json)
                            .setHeader("mqtt_topic", topic)
                            .build()
            );
            System.out.println("📤 Sent to " + topic + " => " + json);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void send(String data) {
        mqttOutboundChannel.send(MessageBuilder.withPayload(data).build());
    }

    @ServiceActivator(inputChannel = "sendData")
    public void sendData(Map<String, Object> payload) {
        System.out.println("📡 Data: " + payload);
    }

    @ServiceActivator(inputChannel = "sendLiveData")
    public void sendLiveData(Map<String, Object> payload) {
        String deviceId = payload.get("device_id").toString();
        if (deviceId.isEmpty() || deviceId.equals("null")) {
            System.err.println("No device found");
        }
        var event  = new LiveEvent();
        event.setPayload(payload);
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
        System.out.println("✅ Ack: " + payload);
    }
}
