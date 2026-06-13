package dev.automata.automata.service;

import dev.automata.automata.model.RecordingSession;
import dev.automata.automata.repository.RecordingBucketRepository;
import dev.automata.automata.repository.RecordingSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the lifecycle of recording sessions.
 * <p>
 * Trigger types
 * ─────────────
 * MANUAL     → user calls startSession() / stopSession() via REST
 * CONDITION  → MqttService calls evaluateConditions() on every message;
 * this service auto-starts when startCondition is satisfied
 * AUTOMATION → AutomationOrchestrator calls startFromAutomation() as an action
 * <p>
 * When a session starts, its deviceIds are registered in Redis via
 * RecordingBufferService so the MQTT listener can look them up cheaply.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordingSessionService {

    private final RecordingSessionRepository sessionRepository;
    private final RecordingBucketRepository bucketRepository;
    private final RecordingBufferService bufferService;
    private final SimpMessagingTemplate messagingTemplate;

    // ──────────────────────────────────────────────────────────────
    // CRUD / MANUAL TRIGGER
    // ──────────────────────────────────────────────────────────────

    /**
     * Creates a session in PENDING state. For MANUAL sessions, immediately
     * calls {@link #startSession(String)} so the caller gets one step.
     */
    public RecordingSession createSession(RecordingSession.TriggerType type,
                                          String name,
                                          List<String> deviceIds,
                                          RecordingSession.RecordingCondition startCondition,
                                          RecordingSession.RecordingCondition stopCondition,
                                          long durationLimitSecs) {
        RecordingSession session = RecordingSession.builder()
                .id(UUID.randomUUID().toString())
                .userId("")
                .name(name)
                .deviceIds(deviceIds)
                .triggerType(type)
                .startCondition(startCondition)
                .stopCondition(stopCondition)
                .durationLimitSecs(durationLimitSecs)
                .status(RecordingSession.SessionStatus.PENDING)
                .createdAt(new Date())
                .updatedAt(new Date())
                .build();

        session = sessionRepository.save(session);
        log.info("📋 [recording] Session '{}' created (type={}, devices={})",
                session.getId(), type, deviceIds);

        if (type == RecordingSession.TriggerType.MANUAL) {
            startSession(session.getId());
        }
        return session;
    }

    /**
     * Transitions session PENDING → ACTIVE and registers it in Redis.
     */
    public RecordingSession startSession(String sessionId) {
        RecordingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        if (session.getStatus() == RecordingSession.SessionStatus.ACTIVE) {
            log.warn("⚠️ [recording] Session '{}' already active — ignoring start", sessionId);
            return session;
        }

        session.setStatus(RecordingSession.SessionStatus.ACTIVE);
        session.setStartTime(new Date());
        session.setUpdatedAt(new Date());
        session = sessionRepository.save(session);

        // Register in Redis so MQTT listener can find it in O(1)
        bufferService.activateSession(sessionId, session.getDeviceIds());

        broadcastSessionEvent(session, "STARTED");
        log.info("▶️ [recording] Session '{}' started", sessionId);
        return session;
    }

    /**
     * Transitions session ACTIVE → STOPPED. Flush job will drain remaining buffer.
     */
    public RecordingSession stopSession(String sessionId) {
        RecordingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        if (session.getStatus() == RecordingSession.SessionStatus.STOPPED) {
            return session;
        }

        session.setStatus(RecordingSession.SessionStatus.STOPPED);
        session.setEndTime(new Date());
        session.setUpdatedAt(new Date());
        session = sessionRepository.save(session);

        // Remove from active set — flush job will do a final drain
        bufferService.deactivateSession(sessionId);

        broadcastSessionEvent(session, "STOPPED");
        log.info("⏹️ [recording] Session '{}' stopped", sessionId);
        return session;
    }

    // ──────────────────────────────────────────────────────────────
    // AUTOMATION TRIGGER
    // ──────────────────────────────────────────────────────────────

    /**
     * Called by AutomationOrchestrator / ActionDispatcher when an automation
     * fires a RECORDING_START action.
     *
     * @param automationId source automation (stored for dedup)
     * @param deviceIds    devices to record (from automation action config)
     * @param durationSecs 0 = no limit
     */
    public RecordingSession startFromAutomation(String automationId,
                                                String automationName,
                                                List<String> deviceIds,
                                                long durationSecs) {
        // Idempotency: don't create a second session if one is already running for this automation
        sessionRepository.findBySourceAutomationIdAndStatus(
                        automationId, RecordingSession.SessionStatus.ACTIVE)
                .ifPresent(existing -> {
                    log.info("ℹ️ [recording] Automation '{}' already has active session '{}'",
                            automationId, existing.getId());
                });

        RecordingSession session = RecordingSession.builder()
                .id(UUID.randomUUID().toString())
                .name("Auto: " + automationName)
                .deviceIds(deviceIds)
                .triggerType(RecordingSession.TriggerType.AUTOMATION)
                .sourceAutomationId(automationId)
                .durationLimitSecs(durationSecs)
                .status(RecordingSession.SessionStatus.PENDING)
                .createdAt(new Date())
                .updatedAt(new Date())
                .build();

        session = sessionRepository.save(session);
        return startSession(session.getId());
    }

    public RecordingSession stopFromAutomation(String automationId) {
        return sessionRepository.findBySourceAutomationIdAndStatus(
                        automationId, RecordingSession.SessionStatus.ACTIVE)
                .map(s -> stopSession(s.getId()))
                .orElse(null);
    }

    // ──────────────────────────────────────────────────────────────
    // CONDITION EVALUATION (called per MQTT message)
    // ──────────────────────────────────────────────────────────────

    /**
     * Called by MqttService on every incoming payload.
     * Evaluates start/stop conditions for sessions that watch this device.
     * This runs synchronously on the MQTT thread — keep it fast.
     *
     * @param deviceId incoming device
     * @param payload  raw MQTT payload
     */
    public void evaluateConditions(String deviceId, Map<String, Object> payload) {
        List<RecordingSession> candidates =
                sessionRepository.findPendingOrActiveByConditionDevice(deviceId);

        for (RecordingSession session : candidates) {
            try {
                if (session.getStatus() == RecordingSession.SessionStatus.PENDING
                        && session.getStartCondition() != null
                        && session.getStartCondition().getDeviceId().equals(deviceId)) {

                    if (conditionMatches(session.getStartCondition(), payload)) {
                        log.info("✅ [recording] Start condition met for session '{}' by device '{}'",
                                session.getId(), deviceId);
                        startSession(session.getId());
                    }

                } else if (session.getStatus() == RecordingSession.SessionStatus.ACTIVE
                        && session.getStopCondition() != null
                        && session.getStopCondition().getDeviceId().equals(deviceId)) {

                    if (conditionMatches(session.getStopCondition(), payload)) {
                        log.info("🛑 [recording] Stop condition met for session '{}' by device '{}'",
                                session.getId(), deviceId);
                        stopSession(session.getId());
                    }
                }
            } catch (Exception e) {
                log.error("❌ [recording] Condition eval error for session '{}': {}",
                        session.getId(), e.getMessage());
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    // QUERIES
    // ──────────────────────────────────────────────────────────────

    public List<RecordingSession> getSessionsForUser() {
        return sessionRepository.findByOrderByCreatedAtDesc();
    }

    public List<RecordingSession> getActiveSessions() {
        return sessionRepository.findByStatus(RecordingSession.SessionStatus.ACTIVE);
    }

    public RecordingSession getSession(String sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
    }

    /**
     * Increments the read-count counter in Mongo (called by flush job).
     */
    public void incrementRecordCount(String sessionId, long delta) {
        sessionRepository.findById(sessionId).ifPresent(s -> {
            s.setRecordCount(s.getRecordCount() + delta);
            s.setUpdatedAt(new Date());
            sessionRepository.save(s);
        });
    }

    /**
     * Deletes a session and all its associated RecordingBucket documents.
     * Refuses to delete an ACTIVE session — call stopSession() first.
     */
    public void deleteSession(String sessionId) {
        RecordingSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        if (session.getStatus() == RecordingSession.SessionStatus.ACTIVE) {
            throw new IllegalStateException("Cannot delete an active session. Stop it first.");
        }

        // Clean up Redis buffer keys if any remain (e.g. PENDING session never started)
        bufferService.deactivateSession(sessionId);
        bufferService.cleanupSession(sessionId);

        // Delete all bucket documents for this session
        bucketRepository.deleteBySessionId(sessionId);

        // Delete the session document itself
        sessionRepository.deleteById(sessionId);

        log.info("🗑️ [recording] Session '{}' and all its data deleted", sessionId);
    }
    // ──────────────────────────────────────────────────────────────
    // CONDITION MATCHING
    // ──────────────────────────────────────────────────────────────

    private boolean conditionMatches(RecordingSession.RecordingCondition condition,
                                     Map<String, Object> payload) {
        if (condition == null || condition.getField() == null) return false;
        Object raw = payload.get(condition.getField());
        if (raw == null) return false;

        try {
            double actual = Double.parseDouble(raw.toString());
            double expected = Double.parseDouble(condition.getValue());

            return switch (condition.getOperator()) {
                case "GT" -> actual > expected;
                case "GTE" -> actual >= expected;
                case "LT" -> actual < expected;
                case "LTE" -> actual <= expected;
                case "EQ" -> actual == expected;
                case "NEQ" -> actual != expected;
                default -> raw.toString().equals(condition.getValue());
            };
        } catch (NumberFormatException e) {
            // Non-numeric: fall back to string equality
            return raw.toString().equals(condition.getValue());
        }
    }

    // ──────────────────────────────────────────────────────────────
    // WEBSOCKET BROADCAST
    // ──────────────────────────────────────────────────────────────

    private void broadcastSessionEvent(RecordingSession session, String event) {
        messagingTemplate.convertAndSend("/topic/recording",
                Map.of("event", event, "session", session));
    }
}