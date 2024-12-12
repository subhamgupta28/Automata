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

import java.util.ArrayList;
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
        var trigger = new Automation.Trigger();
        trigger.setType("state");
        trigger.setKey("range");
        trigger.setDeviceId("670e9f4d4f15cd01e98640cf");
        trigger.setValue("240");

        var action1 = new Automation.Action();
        action1.setData("255");
        action1.setKey("pwm");
        action1.setDeviceId("6713fd6118af335020f90f73");

        var action2 = new Automation.Action();
        action2.setData("");
        action2.setKey("onOff");
        action2.setDeviceId("67571bf46f2d631aa77cc632");

        var condition = new Automation.Condition();
        condition.setCondition("numeric");
        condition.setBelow("300");
        condition.setAbove("200");
        condition.setValueType("int");
        condition.setValue("250");

        var action = Automation.builder()
                .trigger(trigger)
                .name("When motion is detected turn on the lights")
                .actions(List.of(action1, action2))
                .conditions(List.of(condition))
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
