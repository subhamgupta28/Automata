package dev.automata.automata.controller;


import dev.automata.automata.model.Automation;
import dev.automata.automata.model.AutomationDetail;
import dev.automata.automata.service.AutomationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequestMapping("/api/v1/action")
@Controller
@RequiredArgsConstructor
public class AutomationController {

    private final AutomationService actionService;
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping("/send/{deviceId}")
    public ResponseEntity<?> sendConditionToDevice(@PathVariable String deviceId){
        return ResponseEntity.ok(actionService.sendConditionToDevice(deviceId));
    }

//    @GetMapping
    public ResponseEntity<Automation> createAction() {
        var trigger = new Automation.Trigger();
        trigger.setType("periodic");
        trigger.setKey("percent");
        trigger.setDeviceId("6713fd6118af335020f90f73");
        trigger.setValue("25");

        var action1 = new Automation.Action();
        action1.setData("0");
        action1.setKey("pwm");
        action1.setDeviceId("6713fd6118af335020f90f73");

        var action2 = new Automation.Action();
        action2.setData("4");
        action2.setKey("preset");
        action2.setDeviceId("67571bf46f2d631aa77cc632");

        var action3 = new Automation.Action();
        action3.setData("0");
        action3.setKey("speed");
        action3.setDeviceId("673b8250da1ad94ac1d28280");

        var action4 = new Automation.Action();
        action4.setData("0");
        action4.setKey("pwm1");
        action4.setDeviceId("67438bbee4015a53b43788cc");

        var action5 = new Automation.Action();
        action5.setData("10");
        action5.setKey("bright");
        action5.setDeviceId("67571bf46f2d631aa77cc632");

        var action6 = new Automation.Action();
        action6.setData("10");
        action6.setKey("buzzer");
        action6.setDeviceId("67571bf46f2d631aa77cc632");

        var condition = new Automation.Condition();
        condition.setCondition("numeric");
        condition.setBelow("60");
        condition.setAbove("0");
        condition.setValueType("int");
        condition.setValue("1");
        condition.setIsExact(false);

        var action = Automation.builder()
                .trigger(trigger)
                .name("When battery is below 25% turn off everything and alert")
                .actions(List.of(action1, action2, action3, action4, action5))
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

    @GetMapping("/rebootAllDevices")
    public ResponseEntity<String> rebootAllDevices() {
        return ResponseEntity.ok(actionService.rebootAllDevices());
    }

    @MessageMapping("/ackAction")
    public String ackAction(
            @Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor
    ){
        System.err.println("got acknowledge message: " + payload);
        String deviceId = payload.get("device_id").toString();
        if (deviceId.isEmpty() || deviceId.equals("null")) {
            System.err.println("Device Id not found");
        }
        return actionService.ackAction(deviceId, payload);
    }

    @PostMapping("/saveAutomationDetail")
    public ResponseEntity<String> saveAutomationDetail(@RequestBody AutomationDetail automation) {
        return ResponseEntity.ok(actionService.saveAutomationDetail(automation));
    }

    @GetMapping("/getAutomationDetail/{id}")
    public ResponseEntity<AutomationDetail> getAutomationDetail(@PathVariable("id") String id) {
        return ResponseEntity.ok(actionService.getAutomationDetail(id));
    }

    @GetMapping("/disable/{id}/{enabled}")
    public ResponseEntity<String> disableAutomation(@PathVariable String id, @PathVariable Boolean enabled) {
        return ResponseEntity.ok(actionService.disableAutomation(id, enabled));
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
