package dev.automata.automata.controller;

import dev.automata.automata.model.EnergyStat;
import dev.automata.automata.model.VirtualDevice;
import dev.automata.automata.service.VirtualDeviceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<VirtualDevice> getVirtualDevice(@PathVariable String vid) {
        return ResponseEntity.ok(virtualDeviceService.getVirtualDevice(vid));
    }

    @GetMapping("energyChart/{vid}/{param}")
    public ResponseEntity<?> getEnergyAnalyticsChart(@PathVariable String vid, @PathVariable String param){
        return ResponseEntity.ok(virtualDeviceService.getEnergyAnalyticsChart(vid, param));
    }

    @GetMapping("deviceList")
    public ResponseEntity<List<VirtualDevice>> getVirtualDeviceList() {
        return ResponseEntity.ok(virtualDeviceService.getVirtualDeviceList());
    }
    @GetMapping("/showVirtualDevice/{vid}/{isVisible}")
    public ResponseEntity<String> showVirtualDevice(@PathVariable String vid, @PathVariable String isVisible) {
        return ResponseEntity.ok(virtualDeviceService.showCharts(vid, isVisible));
    }
    @PostMapping("create")
    public ResponseEntity<VirtualDevice> createVirtualDevice(@RequestBody VirtualDevice virtualDevice) {
        return ResponseEntity.ok(virtualDeviceService.createVirtualDevice(virtualDevice));
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
    public ResponseEntity<?> deviceTrend(@PathVariable String deviceId) {
        return ResponseEntity.ok(virtualDeviceService.getDeviceTrend(deviceId));
    }

    /* =====================================================
       CHART-READY HOURLY DATA
       ===================================================== */
    @GetMapping("env/hourly/{deviceId}")
    public ResponseEntity<?> hourlyTrend(@PathVariable String deviceId) {
        return ResponseEntity.ok(virtualDeviceService.hourlyTrend(deviceId));
    }
}
