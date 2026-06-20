package dev.automata.automata.controller;


import dev.automata.automata.dto.ChartDataDto;
import dev.automata.automata.dto.DataDto;
import dev.automata.automata.dto.DeviceLoginRequest;
import dev.automata.automata.dto.RegisterDevice;
import dev.automata.automata.model.AttributeType;
import dev.automata.automata.model.Device;
import dev.automata.automata.model.Status;
import dev.automata.automata.model.Users;
import dev.automata.automata.service.AnalyticsService;
import dev.automata.automata.service.MainService;
import dev.automata.automata.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequestMapping("/api/v1/main")
@Controller
@RequiredArgsConstructor
public class MainController {

    private final SimpMessagingTemplate messagingTemplate;
    private final MainService mainService;
    private final AnalyticsService analyticsService;
    private final NotificationService notificationService;

//    private final KafkaTemplate<String, String> kafkaTemplate;

    @GetMapping("healthCheck")
    public ResponseEntity<?> healthCheck(@AuthenticationPrincipal Users user) {
//        mqttService.sendToTopic("automata/action/68c355de653e5d47410e878c", Map.of("key", "val"));
        return ResponseEntity.ok("ok");
    }

    @GetMapping("updateDevice")
    public ResponseEntity<?> updateDevice(@AuthenticationPrincipal Users user) {
        mainService.saveDevice();

        return ResponseEntity.ok("ok");
    }

    @PostMapping("serverCreds")
    public ResponseEntity<?> getServerCreds(@AuthenticationPrincipal Users user) {
        return ResponseEntity.ok(mainService.getServerCreds());
    }

    @PostMapping("wifiList")
    public ResponseEntity<?> getWiFiList(@AuthenticationPrincipal Users user) {
        return ResponseEntity.ok(mainService.getWiFiList());
    }

    @PostMapping("saveWiFiList")
    public ResponseEntity<?> saveWiFiList(@RequestBody Map<String, String> body, @AuthenticationPrincipal Users user) {

        return ResponseEntity.ok(mainService.saveWiFiList(body));
    }

    @GetMapping("chart/{deviceId}/{attribute}")
    public ResponseEntity<ChartDataDto> getChartData(
            @RequestHeader("X-Home-Id") String homeId,
            @PathVariable String deviceId,
            @PathVariable String attribute,
            @AuthenticationPrincipal Users user
    ) {
        return ResponseEntity.ok(analyticsService.getChartData2(deviceId, attribute, "day"));
    }

    @GetMapping("chartDetail/{deviceId}/{range}")
    public ResponseEntity<ChartDataDto> getChartDetail(
            @RequestHeader("X-Home-Id") String homeId,
            @PathVariable String deviceId,
            @PathVariable String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @AuthenticationPrincipal Users user
    ) {
        return ResponseEntity.ok(
                analyticsService.getChartDetail(deviceId, range, start, end)
        );
    }

    @GetMapping("pieChart/{deviceId}")
    public ResponseEntity<ChartDataDto> getPieChartData(
            @RequestHeader("X-Home-Id") String homeId,
            @PathVariable String deviceId,
            @AuthenticationPrincipal Users user
    ) {
        return ResponseEntity.ok(analyticsService.getPieChartData(deviceId, "day"));
    }

    @PostMapping("/saveAttributeType")
    public ResponseEntity<AttributeType> saveAttributeType(
            @RequestBody AttributeType attributeType,
            @AuthenticationPrincipal Users user,
            @RequestHeader("X-Home-Id") String homeId
    ) {
        return ResponseEntity.ok(mainService.createAttributeType(attributeType));
    }

    @GetMapping
    public ResponseEntity<String> status(@AuthenticationPrincipal Users user) {
        notificationService.sendNotification("hello", "medium");
        return ResponseEntity.ok("success");
    }

    @GetMapping("/mainNodePos")
    public ResponseEntity<Map<String, Object>> getMainNodePos(
            @RequestHeader("X-Home-Id") String homeId,
            @AuthenticationPrincipal Users user
    ) {
        return ResponseEntity.ok(mainService.getMainNodePos());
    }

    @GetMapping("/updatePosition/{deviceId}/{x}/{y}")
    public ResponseEntity<String> updatePosition(
            @PathVariable String deviceId,
            @PathVariable String x,
            @PathVariable String y,
            @RequestHeader("X-Home-Id") String homeId,
            @AuthenticationPrincipal Users user
    ) {

        return ResponseEntity.ok(mainService.updateDevicePosition(deviceId, x, y, homeId, user));
    }

    @GetMapping("sendAction/{action}/{value}")
    public ResponseEntity<String> sendActionToDevice(
            @RequestHeader("X-Home-Id") String homeId,
            @PathVariable String action, @PathVariable String value,
            @AuthenticationPrincipal Users user
    ) {
        var map = new HashMap<String, Object>();
        map.put("key", action);
        map.put("value", value);
        messagingTemplate.convertAndSend("/topic/action", map);

        return ResponseEntity.ok("sent");
    }

    @PostMapping("/save/{deviceId}")
    public ResponseEntity<String> save(
            @RequestHeader("X-Home-Id") String homeId,
            @PathVariable String deviceId,
            @RequestBody Map<String, Object> payload,
            @AuthenticationPrincipal Users user
    ) {
        return ResponseEntity.ok(mainService.saveData(deviceId, payload));
    }

    @GetMapping("/showInDashboard/{deviceId}/{isVisible}")
    public ResponseEntity<String> showInDashboard(
            @PathVariable String deviceId,
            @RequestHeader("X-Home-Id") String homeId,
            @PathVariable String isVisible,
            @AuthenticationPrincipal Users user
    ) {
        return ResponseEntity.ok(mainService.showInDashboard(deviceId, isVisible, homeId, user));
    }

    @GetMapping(value = "/dashboard")
    public ResponseEntity<List<Device>> getDashboardDevices(
            @RequestHeader("X-Home-Id") String homeId,
            @AuthenticationPrincipal Users user
    ) {

        return ResponseEntity.ok(mainService.getDashboardDevices(homeId, user));
    }

    @GetMapping(value = "/devices")
    public ResponseEntity<List<Device>> getAllDevices(
            @RequestHeader("X-Home-Id") String homeId,
            @AuthenticationPrincipal Users user
    ) {

        return ResponseEntity.ok(mainService.getAllDeviceUi(homeId, user));
    }

    @PostMapping("/device/login")
    public ResponseEntity<?> login(
            @AuthenticationPrincipal Users user,
            @RequestBody DeviceLoginRequest req
    ) {
        return ResponseEntity.ok(mainService.deviceLogin(req));
    }

    @GetMapping(value = "/time")
    public ResponseEntity<String> getServerTime(@AuthenticationPrincipal Users user) {
        LocalDateTime localDate = LocalDateTime.now();
        return ResponseEntity.ok(localDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss a")));
    }

    @GetMapping(value = "/device/{deviceId}")
    public ResponseEntity<Device> getDeviceById(
            @RequestHeader("X-Home-Id") String homeId,
            @PathVariable String deviceId,
            @AuthenticationPrincipal Users user
    ) {
        return ResponseEntity.ok(mainService.getDevice(deviceId));
    }

    @GetMapping(value = "/data/{deviceId}")
    public ResponseEntity<DataDto> getDataByDeviceId(
            @RequestHeader("X-Home-Id") String homeId,
            @PathVariable String deviceId,
            @AuthenticationPrincipal Users user
    ) {
        return ResponseEntity.ok(mainService.getData(deviceId));
    }

    @GetMapping(value = "/lastData/{deviceId}")
    public ResponseEntity<Map<String, Object>> getLastDataByDeviceId(
            @RequestHeader("X-Home-Id") String homeId,
            @PathVariable String deviceId,
            @AuthenticationPrincipal Users user
    ) {
        return ResponseEntity.ok(mainService.getLastData(deviceId, homeId, user));
    }

    @GetMapping("/showCharts/{deviceId}/{isVisible}")
    public ResponseEntity<String> showCharts(
            @PathVariable String deviceId,
            @PathVariable String isVisible,
            @RequestHeader("X-Home-Id") String homeId,
            @AuthenticationPrincipal Users user
    ) {
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
    public ResponseEntity<String> updateAttrCharts(
            @PathVariable String deviceId,
            @PathVariable String attribute,
            @RequestHeader("X-Home-Id") String homeId,
            @PathVariable String isVisible,
            @AuthenticationPrincipal Users user
    ) {

        return ResponseEntity.ok(mainService.updateAttrCharts(deviceId, attribute, isVisible, homeId, user));
    }

    @PostMapping("/masterList")
    public ResponseEntity<?> getMasterList() {

        return ResponseEntity.ok(mainService.getMasterList());
    }

    @PostMapping("/automations")
    public ResponseEntity<String> getAutomations() {

        return ResponseEntity.ok(mainService.getAutomations());
    }

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
    public ResponseEntity<?> updateAttribute(
            @PathVariable String deviceId,
            @PathVariable String attribute,
            @PathVariable String isShow,
            @RequestHeader("X-Home-Id") String homeId,
            @AuthenticationPrincipal Users user
    ) {
        return ResponseEntity.ok(mainService.updateAttribute(deviceId, attribute, isShow));
    }
}
