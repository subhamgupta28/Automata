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

@RequestMapping("/api/v1/virtual")
@Controller
@RequiredArgsConstructor
public class VirtualDeviceController {

    private final VirtualDeviceService virtualDeviceService;


    @GetMapping("device/{vid}")
    public ResponseEntity<VirtualDevice> getVirtualDevice(
            @PathVariable String vid,
            @AuthenticationPrincipal Users user,
            @RequestHeader("X-Home-Id") String homeId
    ) {
        return ResponseEntity.ok(virtualDeviceService.getVirtualDevice(vid, user, homeId));
    }

    @GetMapping("energyChart/{vid}/{param}")
    public ResponseEntity<?> getEnergyAnalyticsChart(
            @PathVariable String vid, @PathVariable String param,
            @AuthenticationPrincipal Users user,
            @RequestHeader("X-Home-Id") String homeId
    ) {
        return ResponseEntity.ok(virtualDeviceService.getEnergyAnalyticsChart(vid, param, user, homeId));
    }

    @GetMapping("deviceList")
    public ResponseEntity<List<VirtualDevice>> getVirtualDeviceList(
            @AuthenticationPrincipal Users user,
            @RequestHeader("X-Home-Id") String homeId
    ) {
        return ResponseEntity.ok(virtualDeviceService.getVirtualDeviceList(user, homeId));
    }

    @GetMapping("/showVirtualDevice/{vid}/{isVisible}")
    public ResponseEntity<String> showVirtualDevice(
            @PathVariable String vid, @PathVariable String isVisible,
            @AuthenticationPrincipal Users user,
            @RequestHeader("X-Home-Id") String homeId
    ) {
        return ResponseEntity.ok(virtualDeviceService.showCharts(vid, isVisible, user, homeId));
    }

    @PostMapping("create")
    public ResponseEntity<VirtualDevice> createVirtualDevice(
            @RequestBody VirtualDevice virtualDevice,
            @AuthenticationPrincipal Users user,
            @RequestHeader("X-Home-Id") String homeId
    ) {
        return ResponseEntity.ok(virtualDeviceService.createVirtualDevice(virtualDevice, user, homeId));
    }

    @GetMapping("/updatePosition/{vid}/{x}/{y}/{width}/{height}")
    public ResponseEntity<String> updatePosition(
            @PathVariable String vid, @PathVariable String x, @PathVariable String y,
            @PathVariable String width, @PathVariable String height,
            @AuthenticationPrincipal Users user,
            @RequestHeader("X-Home-Id") String homeId
    ) {

        return ResponseEntity.ok(virtualDeviceService.updateDevicePosition(vid, x, y, width, height, user, homeId));
    }

    @GetMapping("/energyAnalytics")
    public ResponseEntity<?> getEnergyStatAnalytics(
            @RequestParam List<String> deviceIds,
            @AuthenticationPrincipal Users user,
            @RequestHeader("X-Home-Id") String homeId
    ) {
        return ResponseEntity.ok(virtualDeviceService.getEnergyStatAnalytics(deviceIds, user, homeId));
    }

    @GetMapping("/recentData")
    public ResponseEntity<?> getRecentDeviceData(
            @RequestParam List<String> deviceIds,
            @AuthenticationPrincipal Users user,
            @RequestHeader("X-Home-Id") String homeId
    ) {
        return ResponseEntity.ok(virtualDeviceService.getRecentDeviceData(deviceIds, user, homeId));
    }

    @GetMapping("energyStats/{id}")
    public ResponseEntity<EnergyStat> getEnergyStats(
            @PathVariable String id,
            @AuthenticationPrincipal Users user,
            @RequestHeader("X-Home-Id") String homeId
    ) {
        return ResponseEntity.ok(virtualDeviceService.getEnergyStatById(id, user, homeId));
    }

    /* =====================================================
   DEVICE SUMMARY
   ===================================================== */
    @GetMapping("env/{deviceId}")
    public ResponseEntity<?> deviceTrend(
            @PathVariable String deviceId,
            @AuthenticationPrincipal Users user,
            @RequestHeader("X-Home-Id") String homeId
    ) {
        return ResponseEntity.ok(virtualDeviceService.getDeviceTrend(deviceId, user, homeId));
    }

    /* =====================================================
       CHART-READY HOURLY DATA
       ===================================================== */
    @GetMapping("env/hourly/{deviceId}")
    public ResponseEntity<?> hourlyTrend(
            @PathVariable String deviceId,
            @AuthenticationPrincipal Users user,
            @RequestHeader("X-Home-Id") String homeId
    ) {
        return ResponseEntity.ok(virtualDeviceService.hourlyTrend(deviceId, user, homeId));
    }
}
