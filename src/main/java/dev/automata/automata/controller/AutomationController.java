package dev.automata.automata.controller;


import dev.automata.automata.model.Automation;
import dev.automata.automata.service.AutomationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RequestMapping("/api/v1/action")
@Controller
@RequiredArgsConstructor
public class AutomationController {

    private final AutomationService actionService;
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping
    public ResponseEntity<Automation> createAction() {
        var action = Automation.builder()
//                .condition(">")
//                .producerValueDataType("int")
//                .consumerDeviceId("670edfe8166ab22722fbf728")
//                .producerDeviceId("670ec3bc166ab22722fbf4ea")
//                .consumerKey("pwm1")
//                .producerKey("button")
//                .defaultValue("0")
//                .valueNegativeC("0")
//                .valuePositiveC("255")
//                .displayName("Motion triggered buzzer")
                .build();

        return ResponseEntity.ok(actionService.create(action));
    }

    @GetMapping("/getAction")
    public ResponseEntity<List<Automation>> getActions() {
        return ResponseEntity.ok(actionService.getActions());
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
        return actionService.handleAction(deviceId, payload, "");
    }


    @PostMapping("/sendAction/{deviceId}/{deviceType}")
    public ResponseEntity<String> handleAction(
            @RequestBody Map<String, Object> payload,
            @PathVariable String deviceId,
            @PathVariable String deviceType
    ) {
        System.err.println("got action message: " + payload);
        return ResponseEntity.ok(actionService.handleAction(deviceId, payload, deviceType));
    }
}
