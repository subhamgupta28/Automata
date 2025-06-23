package dev.automata.automata.controller;

import dev.automata.automata.dto.ChartDataDto;
import dev.automata.automata.dto.DataDto;
import dev.automata.automata.dto.RegisterDevice;
import dev.automata.automata.model.AttributeType;
import dev.automata.automata.model.Device;
import dev.automata.automata.model.Status;
import dev.automata.automata.service.AnalyticsService;
import dev.automata.automata.service.MainService;
import dev.automata.automata.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RequestMapping("/api/v1/main")
@Controller
@RequiredArgsConstructor
public class MainController {

    private final SimpMessagingTemplate messagingTemplate;
    private final MainService mainService;
    private final AnalyticsService analyticsService;
    private final ApplicationEventPublisher publisher;
    private final NotificationService notificationService;
//    private final KafkaTemplate<String, String> kafkaTemplate;

    @PostMapping("wifiList")
    public ResponseEntity<?> getWiFiList() {
        return ResponseEntity.ok(mainService.getWiFiList());
    }

    @PostMapping("saveWiFiList")
    public ResponseEntity<?> saveWiFiList(@RequestBody Map<String, String> body) {

        return ResponseEntity.ok(mainService.saveWiFiList(body));
    }

    @GetMapping("chart/{deviceId}/{attribute}")
    public ResponseEntity<ChartDataDto> getChartData(@PathVariable String deviceId, @PathVariable String attribute) {
        return ResponseEntity.ok(analyticsService.getChartData2(deviceId, attribute, "day"));
    }

    @GetMapping("chartDetail/{deviceId}/{range}")
    public ResponseEntity<ChartDataDto> getChartDetail(@PathVariable String deviceId, @PathVariable String range) {
        return ResponseEntity.ok(analyticsService.getChartDetail(deviceId, range));
    }

    @GetMapping("pieChart/{deviceId}")
    public ResponseEntity<ChartDataDto> getPieChartData(@PathVariable String deviceId) {
        return ResponseEntity.ok(analyticsService.getPieChartData(deviceId, "day"));
    }

    @PostMapping("/saveAttributeType")
    public ResponseEntity<AttributeType> saveAttributeType(@RequestBody AttributeType attributeType) {
        return ResponseEntity.ok(mainService.createAttributeType(attributeType));
    }

    @GetMapping
    public ResponseEntity<String> status() {
        notificationService.sendNotification("hello", "medium");
        return ResponseEntity.ok("success");
    }

    @GetMapping("/mainNodePos")
    public ResponseEntity<Map<String, Object>> getMainNodePos() {
        return ResponseEntity.ok(mainService.getMainNodePos());
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

    @GetMapping("/showInDashboard/{deviceId}/{isVisible}")
    public ResponseEntity<String> showInDashboard(@PathVariable String deviceId, @PathVariable String isVisible) {
        return ResponseEntity.ok(mainService.showInDashboard(deviceId, isVisible));
    }

    @GetMapping(value = "/dashboard")
    public ResponseEntity<List<Device>> getDashboardDevices() {

        return ResponseEntity.ok(mainService.getDashboardDevices());
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

    @GetMapping("/showCharts/{deviceId}/{isVisible}")
    public ResponseEntity<String> showCharts(@PathVariable String deviceId, @PathVariable String isVisible) {
        return ResponseEntity.ok(mainService.showCharts(deviceId, isVisible));
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerDevice(
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

    @GetMapping("/updateAttribute/{deviceId}/{attribute}/{isShow}")
    public ResponseEntity<?> updateAttribute(@PathVariable String deviceId, @PathVariable String attribute, @PathVariable String isShow){
        return ResponseEntity.ok(mainService.updateAttribute(deviceId, attribute, isShow));
    }

    // for saving data from devices
    @PostMapping("sendData")
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
        headerAccessor.getSessionAttributes().put("deviceId", deviceId);
        var map = new HashMap<String, Object>();
        map.put("deviceId", deviceId);
        map.put("data", payload);
        map.put("deviceConfig", device.get("deviceConfig"));
        messagingTemplate.convertAndSend("/topic/data", map);
        return map;
    }

    // for getting live data from devices
    @PostMapping("sendLiveData")
    public ResponseEntity<Map<String, Object>> httpSendLiveData(
            @RequestBody Map<String, Object> payload
    ) {
        System.err.println("got live data via http" + payload);
        String deviceId = payload.get("device_id").toString();
        if (deviceId.isEmpty() || deviceId.equals("null")) {
            System.err.println("No device found");
        }
        return ResponseEntity.ok(getStringObjectMap(payload, null, deviceId));
    }

    @MessageMapping("/sendLiveData")
    public Map<String, Object> sendLiveData(
            @Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor
    ) {
//        System.err.println("got live message: " + payload);
        String deviceId = payload.get("device_id").toString();
        if (deviceId.isEmpty() || deviceId.equals("null")) {
            System.err.println("No device found");
        }
//        var event  = new LiveEvent();
//        event.setPayload(payload);
//        publisher.publishEvent(event);
        return getStringObjectMap(payload, headerAccessor, deviceId);
    }

//    @Value("${kafka.topic}")
//    private String topic;

//    @PostMapping("/send")
//    public String sendMessage(String message) {
//        kafkaTemplate.send(topic, message);
//        return "Message sent to Kafka: " + message;
//    }


    private Map<String, Object> getStringObjectMap(@Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor, String deviceId) {
        var map = new HashMap<String, Object>();
        map.put("deviceId", deviceId);
        map.put("data", payload);

        messagingTemplate.convertAndSend("/topic/data", map);
//        headerAccessor.getSessionAttributes().put("deviceId", deviceId);
        return payload;
    }
}
