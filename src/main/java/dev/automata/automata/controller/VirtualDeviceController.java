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

    @GetMapping("deviceList")
    public ResponseEntity<List<VirtualDevice>> getVirtualDeviceList() {
        return ResponseEntity.ok(virtualDeviceService.getVirtualDeviceList());
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

    @GetMapping("/recentData")
    public ResponseEntity<?> getRecentDeviceData(@RequestParam List<String> deviceIds){
        return ResponseEntity.ok(virtualDeviceService.getRecentDeviceData(deviceIds));
    }

    @GetMapping("energyStats/{id}")
    public ResponseEntity<EnergyStat> getEnergyStats(@PathVariable String id) {
        return ResponseEntity.ok(virtualDeviceService.getTodayStats(id));
    }
}
