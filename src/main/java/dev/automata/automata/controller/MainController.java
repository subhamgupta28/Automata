package dev.automata.automata.controller;

import dev.automata.automata.dto.DataDto;
import dev.automata.automata.dto.RegisterDevice;
import dev.automata.automata.model.Data;
import dev.automata.automata.model.Device;
import dev.automata.automata.service.MainService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RequestMapping("/api/v1/main")
@Controller
@RequiredArgsConstructor
public class MainController {

    private final SimpMessagingTemplate messagingTemplate;
    private final MainService mainService;

    @GetMapping
    public ResponseEntity<String> status(){
        return ResponseEntity.ok("Hello World");
    }

    @PostMapping("/save/{deviceId}")
    public ResponseEntity<String> save(
            @PathVariable String deviceId,
            @RequestBody Map<String, Object> payload
    ) {
        return ResponseEntity.ok(mainService.saveData(deviceId, payload));
    }

    @GetMapping(value = "/device/{deviceId}")
    public ResponseEntity<Device> getConfig(@PathVariable String deviceId){
        return ResponseEntity.ok(mainService.getDevice(deviceId));
    }

    @GetMapping(value = "/data/{deviceId}")
    public ResponseEntity<DataDto> getData(@PathVariable String deviceId){
        return ResponseEntity.ok(mainService.getData(deviceId));
    }

    @PostMapping("/register")
    public ResponseEntity<Device> registerDevice(
            @RequestBody RegisterDevice registerDevice
    ) {
        return ResponseEntity.ok(mainService.registerDevice(registerDevice));
    }


    @GetMapping("/update/{deviceId}")
    @SendTo("/topic/update")
    public ResponseEntity<String> updateDevice(@PathVariable String deviceId) {
        System.err.println(deviceId);
        Device deviceData = mainService.getDevice(deviceId);
        System.err.println(deviceData);
//        messagingTemplate.convertAndSend("/topic/update", deviceData);
        return ResponseEntity.ok("Success");
    }




    @MessageMapping("/sendData")
//    @SendTo("/topic/register")
    public Map<String, Object> addUser(
            @Payload Map<String, Object> payload
    ){
        System.err.println("got message: " + payload);
        String deviceId = payload.get("device_id").toString();
        mainService.saveData(deviceId, payload);

//        headerAccessor.getSessionAttributes().put("username", chatMessage.getSenderId());
        return payload;
    }
}
