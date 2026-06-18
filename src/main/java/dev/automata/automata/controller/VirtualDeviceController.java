package dev.automata.automata.controller;

import dev.automata.automata.model.EnergyStat;
import dev.automata.automata.model.Users;
import dev.automata.automata.model.VirtualDevice;
import dev.automata.automata.service.VirtualDeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RequestMapping("/api/v1/virtual")
@Controller
@RequiredArgsConstructor
public class VirtualDeviceController {

    private final VirtualDeviceService virtualDeviceService;


    @GetMapping("device/{vid}")
    public ResponseEntity<VirtualDevice> getVirtualDevice(@PathVariable String vid, @AuthenticationPrincipal Users user) {
        return ResponseEntity.ok(virtualDeviceService.getVirtualDevice(vid, user.getId()));
    }

    @GetMapping("energyChart/{vid}/{param}")
    public ResponseEntity<?> getEnergyAnalyticsChart(@RequestHeader("X-Home-Id") String homeId, @PathVariable String vid, @PathVariable String param, @AuthenticationPrincipal Users user){
        return ResponseEntity.ok(virtualDeviceService.getEnergyAnalyticsChart(homeId, vid, param, user.getId()));
    }

    @GetMapping("deviceList")
    public ResponseEntity<List<VirtualDevice>> getVirtualDeviceList(@RequestHeader("X-Home-Id") String homeId, @AuthenticationPrincipal Users user) {
        return ResponseEntity.ok(virtualDeviceService.getVirtualDeviceList(homeId, user.getId()));
    }
    @GetMapping("/showVirtualDevice/{vid}/{isVisible}")
    public ResponseEntity<String> showVirtualDevice(@PathVariable String vid, @PathVariable String isVisible) {
        return ResponseEntity.ok(virtualDeviceService.showCharts(vid, isVisible));
    }
    @PostMapping("create")
    public ResponseEntity<VirtualDevice> createVirtualDevice(@RequestBody VirtualDevice virtualDevice, @AuthenticationPrincipal Users user) {
        return ResponseEntity.ok(virtualDeviceService.createVirtualDevice(virtualDevice, user.getId()));
    }

    @GetMapping("/updatePosition/{vid}/{x}/{y}/{width}/{height}")
    public ResponseEntity<String> updatePosition(
            @PathVariable String vid, @PathVariable String x, @PathVariable String y,
            @PathVariable String width, @PathVariable String height
    ) {

        return ResponseEntity.ok(virtualDeviceService.updateDevicePosition(vid, x, y, width, height));
    }
    @GetMapping("/energyAnalytics")
    public ResponseEntity<?> getEnergyStatAnalytics(@RequestParam List<String> deviceIds){
        return ResponseEntity.ok(virtualDeviceService.getEnergyStatAnalytics(deviceIds));
    }
    @GetMapping("/recentData")
    public ResponseEntity<?> getRecentDeviceData(@RequestParam List<String> deviceIds){
        return ResponseEntity.ok(virtualDeviceService.getRecentDeviceData(deviceIds));
    }

    @GetMapping("energyStats/{id}")
    public ResponseEntity<EnergyStat> getEnergyStats(@PathVariable String id) {
        return ResponseEntity.ok(virtualDeviceService.getEnergyStatById(id));
    }

    /* =====================================================
   DEVICE SUMMARY
   ===================================================== */
    @GetMapping("env/{deviceId}")
    public ResponseEntity<?> deviceTrend(@RequestHeader("X-Home-Id") String homeId, @PathVariable String deviceId, @AuthenticationPrincipal Users user) {
        return ResponseEntity.ok(virtualDeviceService.getDeviceTrend(homeId, deviceId, user.getId()));
    }

    /* =====================================================
       CHART-READY HOURLY DATA
       ===================================================== */
    @GetMapping("env/hourly/{deviceId}")
    public ResponseEntity<?> hourlyTrend(@RequestHeader("X-Home-Id") String homeId, @PathVariable String deviceId, @AuthenticationPrincipal Users user) {
        return ResponseEntity.ok(virtualDeviceService.hourlyTrend(homeId, deviceId, user.getId()));
    }
}