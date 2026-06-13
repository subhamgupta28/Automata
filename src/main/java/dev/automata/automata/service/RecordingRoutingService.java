package dev.automata.automata.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

/**
 * Called by MqttService on every incoming payload.
 * Decides whether the payload should be buffered for any active recording session.
 * <p>
 * This is intentionally a thin facade so MqttService doesn't need to know
 * about Redis keys or session internals.
 * <p>
 * Hot path: all checks are Redis lookups (O(1) set membership) so this adds
 * < 1 ms overhead per message.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordingRoutingService {

    private final RecordingBufferService bufferService;
    private final RecordingSessionService sessionService;

    /**
     * Called for every MQTT payload AFTER it has been forwarded to WebSocket.
     * Buffers the payload into any matching active recording session.
     *
     * @param deviceId the device that produced this payload
     * @param payload  raw MQTT payload (last_seen already stamped by MqttService)
     */
    public void route(String deviceId, Map<String, Object> payload) {
        // 1. Condition evaluation (start / stop condition-triggered sessions)
        //    Runs even if no session is currently active for this device,
        //    because a PENDING session might be waiting for this payload.
        sessionService.evaluateConditions(deviceId, payload);

        // 2. Buffer into active sessions
        Set<String> activeSessions = bufferService.getActiveSessions();
        if (activeSessions.isEmpty()) return;

        for (String sessionId : activeSessions) {
            Set<String> devices = bufferService.getDevicesForSession(sessionId);

            boolean shouldRecord = devices.isEmpty()           // "all devices" mode
                    || devices.contains(deviceId);             // device is in session's list

            if (shouldRecord) {
                bufferService.push(sessionId, deviceId, payload);

                // If session has no device list (records all), register this device
                // dynamically so the flush job knows which buffer keys to pop.
                if (devices.isEmpty()) {
                    bufferService.activateSession(sessionId, java.util.List.of(deviceId));
                }
            }
        }
    }
}