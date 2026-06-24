package dev.automata.automata.controller;


import dev.automata.automata.automation.AutomationAnalytics;
import dev.automata.automata.automation.AutomationAnalyticsService;
import dev.automata.automata.automation.MultiTimezoneAutomationService;
import dev.automata.automata.model.Automation;
import dev.automata.automata.model.AutomationDetail;
import dev.automata.automata.model.Users;
import dev.automata.automata.service.AutomationService;
import dev.automata.automata.service.AutomationUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@RequestMapping("/api/v1/action")
@RestController
@RequiredArgsConstructor
public class AutomationController {

    private final AutomationService automationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final MultiTimezoneAutomationService timezoneService;
    private final AutomationAnalyticsService analyticsService;
    private final AutomationUtils automationUtils;


    @GetMapping("/send/{deviceId}")
    public ResponseEntity<?> sendConditionToDevice(@PathVariable String deviceId) {
        return ResponseEntity.ok(automationService.sendConditionToDevice(deviceId));
    }

    //    @GetMapping
    public ResponseEntity<Automation> createAction() {
        var trigger = new Automation.Trigger();
        trigger.setType("periodic");
        trigger.setKey("percent");
        trigger.setDeviceId("6713fd6118af335020f90f73");
        trigger.setValue("25");

        var action1 = new Automation.Action();
        action1.setData("0");
        action1.setKey("pwm");
        action1.setDeviceId("6713fd6118af335020f90f73");

        var action2 = new Automation.Action();
        action2.setData("4");
        action2.setKey("preset");
        action2.setDeviceId("67571bf46f2d631aa77cc632");

        var action3 = new Automation.Action();
        action3.setData("0");
        action3.setKey("speed");
        action3.setDeviceId("673b8250da1ad94ac1d28280");

        var action4 = new Automation.Action();
        action4.setData("0");
        action4.setKey("pwm1");
        action4.setDeviceId("67438bbee4015a53b43788cc");

        var action5 = new Automation.Action();
        action5.setData("10");
        action5.setKey("bright");
        action5.setDeviceId("67571bf46f2d631aa77cc632");

        var action6 = new Automation.Action();
        action6.setData("10");
        action6.setKey("buzzer");
        action6.setDeviceId("67571bf46f2d631aa77cc632");

        var condition = new Automation.Condition();
        condition.setCondition("numeric");
        condition.setBelow("60");
        condition.setAbove("0");
        condition.setValueType("int");
        condition.setValue("1");
        condition.setIsExact(false);

        var action = Automation.builder()
                .trigger(trigger)
                .name("When battery is below 25% turn off everything and alert")
                .actions(List.of(action1, action2, action3, action4, action5))
                .conditions(List.of(condition))
                .build();

        return ResponseEntity.ok(automationService.create(action));
    }

    @GetMapping("/getAction")
    public ResponseEntity<List<Automation>> getActions(
            @RequestHeader("X-Home-Id") String homeId,
            @AuthenticationPrincipal Users user
    ) {
        return ResponseEntity.ok(automationService.getActions(user, homeId));
    }


    @MessageMapping("/action")
    public String sendAction(
            @Payload Map<String, Object> payload, @RequestHeader("X-Home-Id") String homeId,
            @AuthenticationPrincipal Users user
    ) {
        System.err.println("got action message: " + payload);
        String deviceId = payload.get("device_id").toString();
        if (deviceId.isEmpty() || deviceId.equals("null")) {
            return "Device Id not found";
        }
        var userId = user != null ? user.getId() : "System";
        return automationService.handleAction(deviceId, payload, "", userId, homeId);
    }

    @GetMapping("/rebootAllDevices")
    public ResponseEntity<String> rebootAllDevices(
            @RequestHeader("X-Home-Id") String homeId,
            @AuthenticationPrincipal Users user
    ) {
        return ResponseEntity.ok(automationService.rebootAllDevices(user, homeId));
    }

    @MessageMapping("/ackAction")
    public String ackAction(
            @Payload Map<String, Object> payload, SimpMessageHeaderAccessor headerAccessor
    ) {
        System.err.println("got acknowledge message: " + payload);
        String deviceId = payload.get("device_id").toString();
        if (deviceId.isEmpty() || deviceId.equals("null")) {
            System.err.println("Device Id not found");
        }
        return automationService.ackAction(deviceId, payload);
    }

    @PostMapping("/saveAutomationDetail")
    public ResponseEntity<String> saveAutomationDetail(
            @RequestBody AutomationDetail automation,
            @RequestHeader("X-Home-Id") String homeId,
            @AuthenticationPrincipal Users user
    ) {
        return ResponseEntity.ok(automationService.saveAutomationDetailInternal(automation, user.getId(), homeId));
    }

    @GetMapping("/getAutomationDetail/{id}")
    public ResponseEntity<AutomationDetail> getAutomationDetail(@PathVariable("id") String id) {
        return ResponseEntity.ok(automationService.getAutomationDetail(id));
    }

    @GetMapping("/disable/{id}/{enabled}")
    public ResponseEntity<String> disableAutomation(@PathVariable String id, @PathVariable Boolean enabled) {
        return ResponseEntity.ok(automationService.disableAutomation(id, enabled));
    }

    @PostMapping("/sendAction/{deviceId}/{deviceType}")
    public ResponseEntity<String> handleAction(
            @RequestBody Map<String, Object> payload,
            @PathVariable String deviceId,
            @PathVariable String deviceType,
            @RequestHeader("X-Home-Id") String homeId,
            @AuthenticationPrincipal Users user
    ) {
        return ResponseEntity.ok(automationService.handleAction(deviceId, payload, deviceType, user.getId(), homeId));
    }

    // HIGH PRIORITY ENDPOINTS

    /**
     * Check distributed lock status (debugging)
     */
    @GetMapping("/locks/{automationId}")
    public ResponseEntity<?> checkLockStatus(@PathVariable String automationId) {
        // This would query Redis for lock status
        return ResponseEntity.ok(Map.of(
                "message", "Lock status check not yet implemented"
        ));
    }

    // LOW PRIORITY ENDPOINTS

    /**
     * Get analytics for specific automation (LOW PRIORITY)
     */
    @GetMapping("/{automationId}/analytics")
    public ResponseEntity<AutomationAnalytics> getAutomationAnalytics(
            @PathVariable String automationId,
            @RequestParam(defaultValue = "30") int daysBack) {

        log.info("Fetching analytics for automation: {} ({} days)", automationId, daysBack);

        AutomationAnalytics analytics = analyticsService.getAutomationAnalytics(
                automationId,
                daysBack
        );

        if (analytics == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(analytics);
    }

    /**
     * Get analytics overview for all automations
     */
    @GetMapping("/analytics/overview")
    public ResponseEntity<List<AutomationAnalytics>> getAnalyticsOverview(
            @RequestParam(defaultValue = "7") int daysBack) {

        log.info("Fetching analytics overview for all automations ({} days)", daysBack);

        List<AutomationAnalytics> analytics = analyticsService.getAllAutomationAnalytics(daysBack);

        return ResponseEntity.ok()
                // Cache for 5 minutes on client side
                .cacheControl(org.springframework.http.CacheControl.maxAge(300, java.util.concurrent.TimeUnit.SECONDS))
                .body(analytics);
    }

    /**
     * Get top performing automations
     */
    @GetMapping("/analytics/top-performing")
    public ResponseEntity<List<AutomationAnalytics>> getTopPerforming(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(defaultValue = "30") int daysBack) {

        List<AutomationAnalytics> top = analyticsService.getTopPerformingAutomations(
                limit,
                daysBack
        );

        return ResponseEntity.ok(top);
    }

    /**
     * Get problematic automations (low success rate)
     */
    @GetMapping("/analytics/problematic")
    public ResponseEntity<List<AutomationAnalytics>> getProblematic(
            @RequestParam(defaultValue = "0.7") double successThreshold,
            @RequestParam(defaultValue = "30") int daysBack) {

        List<AutomationAnalytics> problematic = analyticsService.getProblematicAutomations(
                successThreshold,
                daysBack
        );

        return ResponseEntity.ok(problematic);
    }

    /**
     * Get execution timeline
     */
    @GetMapping("/{automationId}/timeline")
    public ResponseEntity<List<Map<String, Object>>> getExecutionTimeline(
            @PathVariable String automationId,
            @RequestParam(defaultValue = "24") int hours) {

        List<Map<String, Object>> timeline = analyticsService.getExecutionTimeline(
                automationId,
                hours
        );

        return ResponseEntity.ok(timeline);
    }

    /**
     * Get device impact analysis
     */
    @GetMapping("/analytics/device-impact")
    public ResponseEntity<Map<String, Long>> getDeviceImpact(
            @RequestParam(defaultValue = "30") int daysBack) {

        Map<String, Long> impact = analyticsService.getDeviceImpactAnalysis(daysBack);

        return ResponseEntity.ok(impact);
    }

    /**
     * Get hourly trigger distribution
     */
    @GetMapping("/analytics/hourly-distribution")
    public ResponseEntity<Map<Integer, Long>> getHourlyDistribution(
            @RequestParam(defaultValue = "30") int daysBack) {

        Map<Integer, Long> distribution = analyticsService.getHourlyTriggerDistribution(daysBack);

        return ResponseEntity.ok(distribution);
    }

    /**
     * Multi-timezone support (LOW PRIORITY)
     */
    @GetMapping("/timezones/available")
    public ResponseEntity<Set<String>> getAvailableTimezones() {
        Set<String> timezones = timezoneService.getAvailableTimezones();
        return ResponseEntity.ok(timezones);
    }

    /**
     * Get common timezone options
     */
    @GetMapping("/timezones/common")
    public ResponseEntity<Map<String, String>> getCommonTimezones() {
        Map<String, String> timezones = timezoneService.getCommonTimezones();
        return ResponseEntity.ok(timezones);
    }

    /**
     * Get current time in specified timezone
     */
    @GetMapping("/timezones/current")
    public ResponseEntity<?> getCurrentTimeInZone(@RequestParam String timezone) {
        if (!timezoneService.isValidTimezone(timezone)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid timezone: " + timezone
            ));
        }

        var currentTime = timezoneService.getCurrentTimeInZone(timezone);
        var offset = timezoneService.getTimezoneOffset(timezone);

        return ResponseEntity.ok(Map.of(
                "timezone", timezone,
                "currentTime", currentTime.toString(),
                "offset", offset
        ));
    }

    /**
     * Calculate time until next trigger
     */
    @GetMapping("/{automationId}/next-trigger")
    public ResponseEntity<?> getNextTriggerTime(
            @PathVariable String automationId,
            @RequestParam(required = false) String timezone) {

        // This would need to access the automation's condition
        // Implementation left as exercise

        return ResponseEntity.ok(Map.of(
                "message", "Next trigger calculation not yet implemented"
        ));
    }

    /**
     * Health check for enhanced features
     */
    @GetMapping("/health")
    public ResponseEntity<?> healthCheck() {
        return ResponseEntity.ok(Map.of(
                "status", "healthy",
                "features", Map.of(
                        "distributedLocking", "enabled",
                        "idempotency", "enabled",
                        "timeout", "enabled",
                        "dailyFireTracking", "enabled",
                        "validation", "enabled",
                        "simulation", "enabled",
                        "analytics", "enabled",
                        "multiTimezone", "enabled"
                ),
                "timestamp", System.currentTimeMillis()
        ));
    }

    @PostMapping("/automation/{id}/snooze")
    public ResponseEntity<String> snooze(
            @PathVariable String id,
            @RequestParam int minutes,
            @RequestHeader("X-Home-Id") String homeId,
            @AuthenticationPrincipal Users user
    ) {
        return ResponseEntity.ok(automationUtils.snoozeAutomation(id, minutes, user, homeId));
    }

    @PostMapping("/automation/{id}/disable-timed")
    public ResponseEntity<String> timedDisable(
            @PathVariable String id,
            @RequestParam(defaultValue = "0") int minutes,
            @RequestHeader("X-Home-Id") String homeId,
            @AuthenticationPrincipal Users user
    ) {
        return ResponseEntity.ok(automationUtils.timedDisableAutomation(id, minutes, user, homeId));
    }

    @PostMapping("/automation/{id}/resume")
    public ResponseEntity<String> resume(
            @PathVariable String id,
            @RequestHeader("X-Home-Id") String homeId,
            @AuthenticationPrincipal Users user
    ) {
        return ResponseEntity.ok(automationUtils.resumeAutomation(id, user, homeId));
    }

    @GetMapping("/automation/{id}/snooze-status")
    public ResponseEntity<Map<String, Object>> status(
            @PathVariable String id,
            @RequestHeader("X-Home-Id") String homeId,
            @AuthenticationPrincipal Users user
    ) {
        return ResponseEntity.ok(automationUtils.getSnoozeStatus(id, user, homeId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // COPY
    // POST /api/automations/{id}/copy
    //
    // Creates a deep copy of the automation with:
    //   - New MongoDB ID (so it's a fully independent document)
    //   - Name suffixed with " (copy)"
    //   - isEnabled = false (must be deliberately enabled after review)
    //   - Fresh updateDate
    //   - New AutomationDetail with matching new ID
    //   - Version snapshot for the new automation
    //   - Does NOT copy logs or version history from the original
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping("/{id}/copy")
    public ResponseEntity<Map<String, Object>> copy(
            @PathVariable String id,
            @RequestHeader("X-Home-Id") String homeId,
            @AuthenticationPrincipal Users user
    ) {
        return ResponseEntity.ok(automationService.copyAutomation(id, user.getId(), homeId));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE
    // DELETE /api/automations/{id}
    //
    // Removes the automation and ALL associated data:
    //   - Automation document
    //   - AutomationDetail (graph layout)
    //   - AutomationLog entries
    //   - AutomationVersion history
    //   - Redis cache keys for this automation
    //   - Cancels any ScheduledAutomationManager jobs
    // ─────────────────────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, String>> delete(
            @PathVariable String id,
            @RequestHeader("X-Home-Id") String homeId,
            @AuthenticationPrincipal Users user
    ) {

        return ResponseEntity.ok(automationService.deleteAutomation(id, user.getId(), homeId));
    }
}
