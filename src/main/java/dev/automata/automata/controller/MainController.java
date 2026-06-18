package dev.automata.automata.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.MessageChannel;
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
    private final ApplicationEventPublisher publisher;
    private final NotificationService notificationService;
    private final MessageChannel mqttOutboundChannel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("healthCheck")
    public ResponseEntity<?> healthCheck() {
        return ResponseEntity.ok("ok");
    }

    @GetMapping("updateDevice")
    public ResponseEntity<?> updateDevice() {
        mainService.saveDevice();
        return ResponseEntity.ok("ok");
    }

    @PostMapping("serverCreds")
    public ResponseEntity<?> getServerCreds() {
        return ResponseEntity.ok(mainService.getServerCreds());
    }

    @PostMapping("wifiList")
    public ResponseEntity<?> getWiFiList() {
        return ResponseEntity.ok(mainService.getWiFiList());
    }

    @PostMapping("saveWiFiList")
    public ResponseEntity<?> saveWiFiList(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(mainService.saveWiFiList(body));
    }

    @GetMapping("chart/{deviceId}/{attribute}")
    public ResponseEntity<ChartDataDto> getChartData(@PathVariable String deviceId, @PathVariable String attribute, @AuthenticationPrincipal Users user) {
        return ResponseEntity.ok(analyticsService.getChartData2(deviceId, attribute, "day"));
    }

    @GetMapping("chartDetail/{deviceId}/{range}")
    public ResponseEntity<ChartDataDto> getChartDetail(
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
    public ResponseEntity<ChartDataDto> getPieChartData(@PathVariable String deviceId, @AuthenticationPrincipal Users user) {
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
    public ResponseEntity<String> updatePosition(@PathVariable String deviceId, @PathVariable String x, @PathVariable String y, @AuthenticationPrincipal Users user) {
        return ResponseEntity.ok(mainService.updateDevicePosition(deviceId, x, y));
    }

    @PostMapping("/save/{deviceId}")
    public ResponseEntity<String> save(
            @PathVariable String deviceId,
            @RequestBody Map<String, Object> payload,
            @AuthenticationPrincipal Users user
    ) {
        return ResponseEntity.ok(mainService.saveData(deviceId, payload));
    }

    @GetMapping("/showInDashboard/{deviceId}/{isVisible}")
    public ResponseEntity<String> showInDashboard(@PathVariable String deviceId, @PathVariable String isVisible, @AuthenticationPrincipal Users user) {
        return ResponseEntity.ok(mainService.showInDashboard(deviceId, isVisible, user.getId()));
    }

    @GetMapping(value = "/dashboard")
    public ResponseEntity<List<Device>> getDashboardDevices(@RequestHeader("X-Home-Id") String homeId, @AuthenticationPrincipal Users user) {
        return ResponseEntity.ok(mainService.getDashboardDevices(homeId, user.getId()));
    }

    @GetMapping(value = "/devices")
    public ResponseEntity<List<Device>> getAllDevices(@RequestHeader("X-Home-Id") String homeId, @AuthenticationPrincipal Users user) {
        return ResponseEntity.ok(mainService.getAllDevice(homeId, user.getId()));
    }

    @PostMapping("/device/login")
    public ResponseEntity<?> login(
            @RequestBody DeviceLoginRequest req
    ) {
        return ResponseEntity.ok(mainService.deviceLogin(req));
    }

    @GetMapping(value = "/time")
    public ResponseEntity<String> getServerTime() {
        LocalDateTime localDate = LocalDateTime.now();
        return ResponseEntity.ok(localDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss a")));
    }

    @GetMapping(value = "/device/{deviceId}")
    public ResponseEntity<Device> getDeviceById(@PathVariable String deviceId, @AuthenticationPrincipal Users user) {
        return ResponseEntity.ok(mainService.getDeviceAPI(deviceId, user.getId()));
    }

    @GetMapping(value = "/data/{deviceId}")
    public ResponseEntity<DataDto> getDataByDeviceId(@PathVariable String deviceId, @AuthenticationPrincipal Users user) {
        return ResponseEntity.ok(mainService.getData(deviceId, user.getId()));
    }

    @GetMapping(value = "/lastData/{deviceId}")
    public ResponseEntity<Map<String, Object>> getLastDataByDeviceId(@PathVariable String deviceId, @AuthenticationPrincipal Users user) {
        return ResponseEntity.ok(mainService.getLastData(deviceId, user.getId()));
    }

    @GetMapping("/showCharts/{deviceId}/{isVisible}")
    public ResponseEntity<String> showCharts(@PathVariable String deviceId, @PathVariable String isVisible, @AuthenticationPrincipal Users user) {
        return ResponseEntity.ok(mainService.showCharts(deviceId, isVisible, user.getId()));
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
    public ResponseEntity<String> updateAttrCharts(@PathVariable String deviceId, @PathVariable String attribute, @PathVariable String isVisible, @AuthenticationPrincipal Users user) {
        return ResponseEntity.ok(mainService.updateAttrCharts(deviceId, attribute, isVisible, user.getId()));
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
    public ResponseEntity<String> updateDevice(@PathVariable String deviceId, @AuthenticationPrincipal Users user) {
        Device deviceData = mainService.getDeviceAPI(deviceId, user.getId());
        messagingTemplate.convertAndSend("/topic/update/" + deviceId, deviceData);
        return ResponseEntity.ok("Success");
    }

    @GetMapping("/updateDevice/{deviceId}")
    public ResponseEntity<String> devicesWs(@PathVariable String deviceId, @AuthenticationPrincipal Users user) {
        var device = mainService.setStatus(deviceId, Status.ONLINE, user.getId());
        messagingTemplate.convertAndSend("/topic/data", device);
        return ResponseEntity.ok("Success");
    }

    @GetMapping("/updateAttribute/{deviceId}/{attribute}/{isShow}")
    public ResponseEntity<?> updateAttribute(@PathVariable String deviceId, @PathVariable String attribute, @PathVariable String isShow, @AuthenticationPrincipal Users user) {
        return ResponseEntity.ok(mainService.updateAttribute(deviceId, attribute, isShow, user.getId()));
    }
}