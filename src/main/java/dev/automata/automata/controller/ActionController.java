package dev.automata.automata.controller;


import dev.automata.automata.model.Actions;
import dev.automata.automata.service.ActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.HashMap;
import java.util.Map;

@RequestMapping("/api/v1/action")
@Controller
@RequiredArgsConstructor
public class ActionController {

    private final ActionService actionService;
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping
    public ResponseEntity<Actions> createAction() {
        var action = Actions.builder()
                .condition(">")
                .producerValueDataType("int")
                .consumerDeviceId("670edfe8166ab22722fbf728")
                .producerDeviceId("670ec3bc166ab22722fbf4ea")
                .consumerKey("pwm1")
                .producerKey("button")
                .defaultValue("0")
                .valueNegativeC("0")
                .valuePositiveC("255")
                .displayName("Motion triggered buzzer")
                .build();

        return ResponseEntity.ok(actionService.create(action));
    }

    // for getting action data from devices
    @MessageMapping("/action")
    public String sendAction(
            @Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor
    ) {
        System.err.println("got action message: " + payload);
        String deviceId = payload.get("device_id").toString();
        if (deviceId.isEmpty() || deviceId.equals("null")) {
            return "Device Id not found";
        }
        return actionService.handleAction(deviceId, payload);
    }
}
