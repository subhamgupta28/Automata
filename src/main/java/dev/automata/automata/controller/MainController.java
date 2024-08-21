package dev.automata.automata.controller;

import dev.automata.automata.dto.RegisterDevice;
import dev.automata.automata.model.Device;
import dev.automata.automata.service.MainService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RequestMapping("/api/v1/main")
@Controller
@RequiredArgsConstructor
public class MainController {

    private final SimpMessagingTemplate messagingTemplate;
    private final MainService mainService;

    @PostMapping("/save/{deviceId}")
    public ResponseEntity<String> save(
            @PathVariable Long deviceId,
            @RequestBody Map<String, Object> payload
    ) {
        return ResponseEntity.ok(mainService.saveData(deviceId, payload));
    }

    @GetMapping("/register")
    public ResponseEntity<Device> registerDevice(
            @RequestBody RegisterDevice registerDevice
    ) {
        return ResponseEntity.ok(mainService.registerDevice(registerDevice));
    }


    @MessageMapping("/sendData")
//    @SendTo("/topic/register")
    public Map<String, Object> addUser(
            @Payload Map<String, Object> payload
    ){
        System.err.println("got message: " + payload);
        long deviceId = Long.parseLong(payload.get("deviceId").toString());
        mainService.saveData(deviceId, payload);

//        headerAccessor.getSessionAttributes().put("username", chatMessage.getSenderId());
        return payload;
    }
}
