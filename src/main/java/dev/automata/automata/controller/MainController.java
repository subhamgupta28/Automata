package dev.automata.automata.controller;

import dev.automata.automata.dto.ChartDataDto;
import dev.automata.automata.dto.DataDto;
import dev.automata.automata.dto.RegisterDevice;
import dev.automata.automata.model.Attribute;
import dev.automata.automata.model.Device;
import dev.automata.automata.model.Status;
import dev.automata.automata.service.AnalyticsService;
import dev.automata.automata.service.MainService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RequestMapping("/api/v1/main")
@Controller
@RequiredArgsConstructor
public class MainController {

    private final SimpMessagingTemplate messagingTemplate;
    private final MainService mainService;
    private final AnalyticsService analyticsService;

    @GetMapping("chart/{deviceId}/{attribute}")
    public ResponseEntity<ChartDataDto> getChartData(@PathVariable String deviceId, @PathVariable String attribute) {
        return ResponseEntity.ok(analyticsService.getChartData2(deviceId, attribute, "day"));
    }

    @GetMapping
    public ResponseEntity<Device> status() {
        var attrs = new ArrayList<Attribute>();
        attrs.add(Attribute.builder()
                .key("onOff")
                .displayName("On Off")
                .units("")
                .visible(true)
                .type("ACTION|OUT")
                .build());
        attrs.add(Attribute.builder()
                .key("bright")
                .displayName("Brightness")
                .units("")
                .visible(true)
                .type("DATA|SLIDER")
                .extras(Map.of("min", 0, "max", 255))
                .build());
        attrs.add(Attribute.builder()
                .key("preset")
                .displayName("Presets")
                .units("")
                .visible(true)
                .type("DATA|PRESET")
                .extras(Map.of("p1", 1, "p2", 2, "p3", 3, "p4", 4))
                .build());
        var reg = RegisterDevice.builder()
                .name("WLED")
                .sleep(false)
                .status(Status.ONLINE)
                .accessUrl("http://192.168.29.196")
                .type("WLED")
                .macAddr("8C:A3:99:CF:FB:AC")
                .updateInterval(30000L)
                .reboot(false)
                .attributes(attrs).build();
//        var res = mainService.registerDevice(reg);
        return ResponseEntity.ok(new Device());
    }

    @GetMapping("/updatePosition/{deviceId}/{x}/{y}")
    public ResponseEntity<String> updatePosition(@PathVariable String deviceId, @PathVariable String x, @PathVariable String y) {

        return ResponseEntity.ok(mainService.updateDevicePosition(deviceId, x, y));
    }

    @GetMapping("sendAction/{action}/{value}")
    public ResponseEntity<String> sendActionToDevice(
            @PathVariable String action, @PathVariable String value
    ) {
        var map = new HashMap<String, Object>();
        map.put("key", action);
        map.put("value", value);
        messagingTemplate.convertAndSend("/topic/action", map);
        return ResponseEntity.ok("sent");
    }

    @PostMapping("/save/{deviceId}")
    public ResponseEntity<String> save(
            @PathVariable String deviceId,
            @RequestBody Map<String, Object> payload
    ) {
        return ResponseEntity.ok(mainService.saveData(deviceId, payload));
    }

    @GetMapping(value = "/devices")
    public ResponseEntity<List<Device>> getAllDevices() {
        return ResponseEntity.ok(mainService.getAllDevice());
    }

    @GetMapping(value = "/time")
    public ResponseEntity<String> getServerTime() {
        LocalDateTime localDate = LocalDateTime.now();
        return ResponseEntity.ok(localDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss a")));
    }

    @GetMapping(value = "/device/{deviceId}")
    public ResponseEntity<Device> getDeviceById(@PathVariable String deviceId) {
        return ResponseEntity.ok(mainService.getDevice(deviceId));
    }

    @GetMapping(value = "/data/{deviceId}")
    public ResponseEntity<DataDto> getDataByDeviceId(@PathVariable String deviceId) {
        return ResponseEntity.ok(mainService.getData(deviceId));
    }

    @GetMapping(value = "/lastData/{deviceId}")
    public ResponseEntity<Map<String, Object>> getLastDataByDeviceId(@PathVariable String deviceId) {
        return ResponseEntity.ok(mainService.getLastData(deviceId));
    }

    @PostMapping("/register")
    public ResponseEntity<Device> registerDevice(
            @RequestBody RegisterDevice registerDevice
    ) {
        var device = mainService.registerDevice(registerDevice);
        var map = new HashMap<String, Object>();
        map.put("deviceId", device.getId());
        map.put("deviceConfig", device);
        map.put("data", new HashMap<>());
        messagingTemplate.convertAndSend("/topic/data", map);
        return ResponseEntity.ok(device);
    }

    @GetMapping("/attrCharts/{deviceId}/{attribute}/{isVisible}")
    public ResponseEntity<String> updateAttrCharts(@PathVariable String deviceId, @PathVariable String attribute, @PathVariable String isVisible) {

        return ResponseEntity.ok(mainService.updateAttrCharts(deviceId, attribute, isVisible));
    }

//    @GetMapping("/attrCharts/{deviceId}")
//    public ResponseEntity<String> getVisibleAttrCharts(@PathVariable String deviceId) {
//
//        return ResponseEntity.ok(mainService.getVisibleAttrCharts(deviceId));
//    }

    @GetMapping("/update/{deviceId}")
    public ResponseEntity<String> updateDevice(@PathVariable String deviceId) {
        System.err.println(deviceId);
        Device deviceData = mainService.getDevice(deviceId);
        System.err.println(deviceData);
        messagingTemplate.convertAndSend("/topic/update/" + deviceId, deviceData);
        return ResponseEntity.ok("Success");
    }


    @GetMapping("/updateDevice/{deviceId}")
    public ResponseEntity<String> devicesWs(@PathVariable String deviceId) {
        var device = mainService.setStatus(deviceId, Status.ONLINE);
        messagingTemplate.convertAndSend("/topic/data", device);
        return ResponseEntity.ok("Success");
    }


    // for saving data from devices
    @MessageMapping("/sendData")
    public Map<String, Object> addUser(
            @Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor
    ) {
        System.err.println("got message: " + payload);
        String deviceId = payload.get("device_id").toString();
        if (deviceId.isEmpty() || deviceId.equals("null")) {
            return payload;
        }
        if (payload.size() > 1)
            mainService.saveData(deviceId, payload);
        var device = mainService.setStatus(deviceId, Status.ONLINE);
//        messagingTemplate.convertAndSend("/topic/data", device);

        var map = new HashMap<String, Object>();
        map.put("deviceId", deviceId);
        map.put("data", payload);
        map.put("deviceConfig", device.get("deviceConfig"));
        messagingTemplate.convertAndSend("/topic/data", map);
        return map;
    }

    // for getting live data from devices
    @MessageMapping("/sendLiveData")
    public Map<String, Object> sendLiveData(
            @Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor
    ) {
//        System.err.println("got live message: " + payload);
        String deviceId = payload.get("device_id").toString();
        if (deviceId.isEmpty() || deviceId.equals("null")) {
            return payload;
        }
        return getStringObjectMap(payload, headerAccessor, deviceId);
    }


    private Map<String, Object> getStringObjectMap(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor, String deviceId) {
        var map = new HashMap<String, Object>();
        map.put("deviceId", deviceId);
        map.put("data", payload);

        messagingTemplate.convertAndSend("/topic/data", map);
        headerAccessor.getSessionAttributes().put("deviceId", deviceId);
        return payload;
    }
}
