package dev.automata.automata.controller;

import dev.automata.automata.automation.AutomationAnalyticsService;
import dev.automata.automata.automation.AutomationVersionService;
import dev.automata.automata.dto.AutomationAnalyticsDto;
import dev.automata.automata.dto.AutomationAnalyticsSummaryDto;
import dev.automata.automata.model.AutomationVersion;
import dev.automata.automata.model.Users;
import dev.automata.automata.service.AutomationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/automations")
@RequiredArgsConstructor
public class AutomationUtilsController {

    private final AutomationVersionService versionService;
    private final AutomationService automationService;
    private final AutomationAnalyticsService analyticsService;


    /**
     * GET /automations/analytics
     * Returns per-automation analytics for the last 24 hours.
     * Sorted by status severity (errors first) by the frontend.
     */
    @GetMapping("analytics")
    public ResponseEntity<List<AutomationAnalyticsDto>> getAnalytics() {
        return ResponseEntity.ok(analyticsService.getAnalytics());
    }

    /**
     * GET /automations/analytics/summary
     * Returns aggregate counts across all automations.
     */
    @GetMapping("analytics/summary")
    public ResponseEntity<AutomationAnalyticsSummaryDto> getSummary() {
        return ResponseEntity.ok(analyticsService.getSummary());
    }

    /**
     * GET /api/automations/{id}/versions
     * Returns all versions for an automation, newest first.
     * Each entry includes the diff from the previous version.
     */
    @GetMapping("{automationId}/versions")
    public ResponseEntity<List<AutomationVersion>> getVersions(
            @PathVariable String automationId) {
        return ResponseEntity.ok(versionService.getVersions(automationId));
    }

    /**
     * GET /api/automations/{id}/versions/{version}
     * Returns a specific version snapshot.
     */
    @GetMapping("{automationId}/versions/{version}")
    public ResponseEntity<AutomationVersion> getVersion(
            @PathVariable String automationId,
            @PathVariable int version) {
        return versionService.getVersion(automationId, version)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * POST /api/automations/{id}/versions/{version}/rollback
     * Rolls back to the specified version.
     * Creates a new version snapshot with a rollback note.
     * <p>
     * Body (optional): { "user": "john", "note": "reverting bad threshold change" }
     */
    @PostMapping("{automationId}/versions/{version}/rollback")
    public ResponseEntity<Map<String, String>> rollback(
            @PathVariable String automationId,
            @PathVariable int version,
            @RequestBody(required = false) Map<String, String> body,
            @RequestHeader("X-Home-Id") String homeId,
            @AuthenticationPrincipal Users user
    ) {

//        String user = body != null ? body.getOrDefault("user", "api") : "api";
        String result = automationService.rollbackToVersion(automationId, version, user.getId(), homeId);
        return ResponseEntity.ok(Map.of("status", result));
    }


    public record AnnotatedSaveRequest(
            dev.automata.automata.model.AutomationDetail detail,
            String savedBy,
            String changeNote) {
    }

}
