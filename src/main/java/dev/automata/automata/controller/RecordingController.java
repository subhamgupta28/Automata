package dev.automata.automata.controller;

import dev.automata.automata.model.RecordingBucket;
import dev.automata.automata.model.RecordingSession;
import dev.automata.automata.model.Users;
import dev.automata.automata.repository.RecordingBucketRepository;
import dev.automata.automata.service.RecordingSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/recordings")
@RequiredArgsConstructor
public class RecordingController {

    private final RecordingSessionService sessionService;
    private final RecordingBucketRepository bucketRepository;

    // ──────────────────────────────────────────────────────────────
    // SESSION MANAGEMENT
    // ──────────────────────────────────────────────────────────────

    /**
     * Create a new recording session.
     * <p>
     * Body example (MANUAL):
     * {
     * "name": "GPS Drive Test",
     * "triggerType": "MANUAL",
     * "deviceIds": ["gps-module-1"],
     * "durationLimitSecs": 3600
     * }
     * <p>
     * Body example (CONDITION):
     * {
     * "name": "Record when speed > 20",
     * "triggerType": "CONDITION",
     * "deviceIds": ["gps-module-1"],
     * "startCondition": { "deviceId": "gps-module-1", "field": "speed", "operator": "GT", "value": "20" },
     * "stopCondition":  { "deviceId": "gps-module-1", "field": "speed", "operator": "LT", "value": "5" },
     * "durationLimitSecs": 0
     * }
     */
    @PostMapping("sessions")

    public ResponseEntity<RecordingSession> create(@RequestBody CreateSessionRequest req) {
        var session = sessionService.createSession(
                req.triggerType(),
                req.name(),
                req.deviceIds(),
                req.startCondition(),
                req.stopCondition(),
                req.durationLimitSecs()
        );
        return ResponseEntity.ok(session);
    }

    /**
     * Manual start (for CONDITION-type sessions that were created but not yet started).
     */
    @PostMapping("sessions/{sessionId}/start")

    public ResponseEntity<RecordingSession> start(@PathVariable String sessionId) {
        return ResponseEntity.ok(sessionService.startSession(sessionId));
    }

    /**
     * Manual stop.
     */
    @PostMapping("sessions/{sessionId}/stop")

    public ResponseEntity<RecordingSession> stop(@PathVariable String sessionId) {
        return ResponseEntity.ok(sessionService.stopSession(sessionId));
    }

    /**
     * List all sessions for the current user.
     */
    @GetMapping("sessions")

    public ResponseEntity<List<RecordingSession>> list(@AuthenticationPrincipal Users currentUser) {
        log.info("User: [{}]", currentUser);
        return ResponseEntity.ok(sessionService.getSessionsForUser());
    }

    /**
     * Get a single session's metadata.
     */
    @GetMapping("/{sessionId}")

    public ResponseEntity<RecordingSession> get(@PathVariable String sessionId) {
        return ResponseEntity.ok(sessionService.getSession(sessionId));
    }

    // ──────────────────────────────────────────────────────────────
    // DATA REPLAY
    // ──────────────────────────────────────────────────────────────

    /**
     * Returns all buckets for a session (all devices), ordered by time.
     * Use for replay / export.
     */
    @GetMapping("/{sessionId}/data")

    public ResponseEntity<List<RecordingBucket>> getData(@PathVariable String sessionId) {
        return ResponseEntity.ok(
                bucketRepository.findBySessionIdOrderByBucketStartAsc(sessionId));
    }

    /**
     * Returns buckets for a specific device within a session.
     * Supports optional pagination (for large sessions).
     */
    @GetMapping("/{sessionId}/data/{deviceId}")

    public ResponseEntity<List<RecordingBucket>> getDeviceData(
            @PathVariable String sessionId,
            @PathVariable String deviceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {

        List<RecordingBucket> buckets = size == 0
                ? bucketRepository.findBySessionIdAndDeviceIdOrderByBucketStartAsc(sessionId, deviceId)
                : bucketRepository.findBySessionIdAndDeviceIdOrderByBucketStartDesc(
                sessionId, deviceId, PageRequest.of(page, size));

        return ResponseEntity.ok(buckets);
    }

    /**
     * Summary: how many buckets / readings exist for a session.
     */
    @GetMapping("/{sessionId}/summary")

    public ResponseEntity<Map<String, Object>> summary(@PathVariable String sessionId) {
        RecordingSession session = sessionService.getSession(sessionId);
        long bucketCount = bucketRepository.countBySessionId(sessionId);
        return ResponseEntity.ok(Map.of(
                "sessionId", sessionId,
                "status", session.getStatus(),
                "recordCount", session.getRecordCount(),
                "bucketCount", bucketCount,
                "startTime", session.getStartTime() != null ? session.getStartTime() : "N/A",
                "endTime", session.getEndTime() != null ? session.getEndTime() : "ongoing"
        ));
    }

    @DeleteMapping("sessions/{sessionId}")
    public ResponseEntity<Void> delete(@PathVariable String sessionId, @AuthenticationPrincipal Users currentUser) {
        sessionService.deleteSession(sessionId);
        log.info("User: [{}]", currentUser);
        return ResponseEntity.noContent().build();
    }
    // ──────────────────────────────────────────────────────────────
    // REQUEST RECORDS
    // ──────────────────────────────────────────────────────────────

    public record CreateSessionRequest(
            String name,
            RecordingSession.TriggerType triggerType,
            List<String> deviceIds,
            RecordingSession.RecordingCondition startCondition,
            RecordingSession.RecordingCondition stopCondition,
            long durationLimitSecs
    ) {
    }
}