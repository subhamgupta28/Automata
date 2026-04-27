package dev.automata.automata.controller;

import dev.automata.automata.automation.AutomationAbTestService;
import dev.automata.automata.automation.AutomationSceneService;
import dev.automata.automata.automation.AutomationVersionService;
import dev.automata.automata.model.AutomationAbTest;
import dev.automata.automata.model.AutomationAbTestLog;
import dev.automata.automata.model.AutomationScene;
import dev.automata.automata.model.AutomationVersion;
import dev.automata.automata.service.AutomationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/automations")
@RequiredArgsConstructor
public class AutomationUtilsController {

    private final AutomationVersionService versionService;
    private final AutomationService automationService;
    private final AutomationSceneService sceneService;
    private final AutomationAbTestService abTestService;

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
            @RequestBody(required = false) Map<String, String> body) {

        String user = body != null ? body.getOrDefault("user", "api") : "api";
        String result = automationService.rollbackToVersion(automationId, version, user);
        return ResponseEntity.ok(Map.of("status", result));
    }

    /**
     * POST /api/automations/save-annotated
     * Saves an automation detail with a user-provided change note.
     * Creates a version snapshot with the note attached.
     * <p>
     * Use this endpoint from the UI when the user fills in "what changed"
     * before saving. Falls back to the regular save endpoint otherwise.
     */
    @PostMapping("/save-annotated")
    public ResponseEntity<Map<String, String>> saveAnnotated(
            @RequestBody AnnotatedSaveRequest request) {
        String result = automationService.saveAutomationDetailAnnotated(
                request.detail(), request.savedBy(), request.changeNote());
        return ResponseEntity.ok(Map.of("status", result));
    }

    public record AnnotatedSaveRequest(
            dev.automata.automata.model.AutomationDetail detail,
            String savedBy,
            String changeNote) {
    }


    /**
     * GET /api/scenes — list all scenes
     */
    @GetMapping("/scenes")
    public ResponseEntity<List<AutomationScene>> findAllScene() {
        return ResponseEntity.ok(sceneService.findAll());
    }

    /**
     * GET /api/scenes/{id}
     */
    @GetMapping("/scenes/{id}")
    public ResponseEntity<AutomationScene> findById(@PathVariable String id) {
        return sceneService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * POST /api/scenes
     * Creates or updates a scene.
     * <p>
     * Body:
     * {
     * "name": "Good Morning",
     * "description": "Wake-up routine",
     * "members": [
     * { "automationId": "...", "order": 1, "delayAfterSeconds": 2 },
     * { "automationId": "...", "order": 2, "delayAfterSeconds": 0 }
     * ]
     * }
     */
    @PostMapping("/scenes")
    public ResponseEntity<AutomationScene> save(@RequestBody AutomationScene scene) {
        return ResponseEntity.ok(sceneService.save(scene));
    }

    /**
     * DELETE /api/scenes/{id}
     */
    @DeleteMapping("/scenes/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        sceneService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * PATCH /api/scenes/{id}/toggle?enabled=true
     */
    @PatchMapping("/scenes/{id}/toggle")
    public ResponseEntity<AutomationScene> toggle(
            @PathVariable String id,
            @RequestParam boolean enabled) {
        return ResponseEntity.ok(sceneService.toggle(id, enabled));
    }

    /**
     * POST /api/scenes/{id}/trigger
     * Fires the scene immediately.
     * Returns immediately — scene executes asynchronously.
     * <p>
     * Body (optional): { "triggeredBy": "john" }
     */
    @PostMapping("/scenes/{id}/trigger")
    public CompletableFuture<ResponseEntity<AutomationSceneService.SceneTriggerResult>> trigger(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body) {

        String triggeredBy = body != null ? body.getOrDefault("triggeredBy", "api") : "api";
        return sceneService.trigger(id, triggeredBy)
                .thenApply(ResponseEntity::ok);
    }

    /**
     * GET /api/ab-tests — list all tests
     */
    @GetMapping("/ab-tests")
    public ResponseEntity<List<AutomationAbTest>> findAll() {
        return ResponseEntity.ok(abTestService.findAll());
    }

    /**
     * GET /api/ab-tests/running
     */
    @GetMapping("/ab-tests/running")
    public ResponseEntity<List<AutomationAbTest>> findRunning() {
        return ResponseEntity.ok(abTestService.findRunning());
    }

    /**
     * POST /api/ab-tests
     * Creates a new A/B test.
     * <p>
     * Body:
     * {
     * "name": "Lux threshold 50 vs 30",
     * "description": "Testing lower threshold for lights",
     * "variantAId": "existing-live-automation-id",
     * "variantBId": "candidate-automation-id"
     * }
     * <p>
     * Variant B must already exist in the DB (create it via the editor first
     * with isEnabled=false). The service will automatically disable it if
     * it was accidentally left enabled.
     */
    @PostMapping("/ab-tests")
    public ResponseEntity<AutomationAbTest> create(
            @RequestBody AutomationAbTest test,
            @RequestParam(defaultValue = "api") String createdBy) {
        return ResponseEntity.ok(abTestService.create(test, createdBy));
    }

    /**
     * POST /api/ab-tests/{id}/pause
     */
    @PostMapping("/ab-tests/{id}/pause")
    public ResponseEntity<AutomationAbTest> pause(@PathVariable String id) {
        return ResponseEntity.ok(abTestService.pause(id));
    }

    /**
     * POST /api/ab-tests/{id}/resume
     */
    @PostMapping("/ab-tests/{id}/resume")
    public ResponseEntity<AutomationAbTest> resume(@PathVariable String id) {
        return ResponseEntity.ok(abTestService.resume(id));
    }

    /**
     * POST /api/ab-tests/{id}/end
     * Ends the test and records the winner.
     * If winner=B, variant B is promoted to live and variant A is disabled.
     * <p>
     * Body:
     * {
     * "winner": "A",           // "A" | "B"
     * "conclusion": "Variant A performed better — fewer false triggers"
     * }
     */
    @PostMapping("/ab-tests/{id}/end")
    public ResponseEntity<AutomationAbTest> end(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {
        String winner = body.getOrDefault("winner", "A");
        String conclusion = body.getOrDefault("conclusion", "");
        return ResponseEntity.ok(abTestService.end(id, winner, conclusion));
    }

    /**
     * GET /api/ab-tests/{id}/logs?limit=50
     * Returns recent evaluation log entries for the test.
     */
    @GetMapping("/ab-tests/{id}/logs")
    public ResponseEntity<List<AutomationAbTestLog>> getLogs(
            @PathVariable String id,
            @RequestParam(defaultValue = "50") int limit) {
        return ResponseEntity.ok(abTestService.getRecentLogs(id, limit));
    }

    /**
     * GET /api/ab-tests/{id}/divergences
     * Returns all log entries where A and B gave different root results.
     * These are the most useful entries for understanding how the variants differ.
     */
    @GetMapping("/ab-tests/{id}/divergences")
    public ResponseEntity<List<AutomationAbTestLog>> getDivergences(@PathVariable String id) {
        return ResponseEntity.ok(abTestService.getDivergences(id));
    }
}
