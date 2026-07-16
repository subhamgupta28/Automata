package dev.automata.automata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.automata.automata.automation_engine.ExecutionPlan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Handles recording-related automation actions.
 * <p>
 * Action types (stored in CompiledAction.key):
 * RECORDING_START  — starts or creates a recording session
 * RECORDING_STOP   — stops the session started by this automation
 * <p>
 * Expected CompiledAction.data fields for RECORDING_START:
 * {
 * "deviceIds":      ["device-id-1", "device-id-2"],   // empty = all devices
 * "durationSecs":   3600,                              // 0 = no limit
 * "name":           "My Recording"                     // optional label
 * }
 * <p>
 * Wire this into ActionDispatcher.dispatch() by adding a branch:
 * <p>
 * case "RECORDING_START" -> recordingAction.handleStart(action, automationId, automationName);
 * case "RECORDING_STOP"  -> recordingAction.handleStop(automationId);
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RecordingAction {

    private final RecordingSessionService sessionService;
    private final ObjectMapper objectMapper;

    @SuppressWarnings("unchecked")
    public void handleStart(ExecutionPlan.CompiledAction action,
                            String automationId,
                            String automationName) {
        try {
            Map<String, Object> data = action.getData() != null
                    ? objectMapper.convertValue(action.getData(), Map.class)
                    : Map.of();

            List<String> deviceIds = data.containsKey("deviceIds")
                    ? (List<String>) data.get("deviceIds")
                    : List.of();

            long durationSecs = data.containsKey("durationSecs")
                    ? Long.parseLong(data.get("durationSecs").toString())
                    : 0L;

            String name = data.containsKey("name")
                    ? data.get("name").toString()
                    : automationName;

            var session = sessionService.startFromAutomation(
                    automationId, name, deviceIds, durationSecs);

            log.info("▶️ [RecordingAction] Started session '{}' for automation '{}'",
                    session.getId(), automationId);

        } catch (Exception e) {
            log.error("❌ [RecordingAction] Failed to start session for automation '{}': {}",
                    automationId, e.getMessage(), e);
        }
    }

    public void handleStop(String automationId) {
        try {
            var stopped = sessionService.stopFromAutomation(automationId);
            if (stopped != null) {
                log.info("⏹️ [RecordingAction] Stopped session '{}' for automation '{}'",
                        stopped.getId(), automationId);
            } else {
                log.warn("⚠️ [RecordingAction] No active session found for automation '{}'", automationId);
            }
        } catch (Exception e) {
            log.error("❌ [RecordingAction] Failed to stop session for automation '{}': {}",
                    automationId, e.getMessage(), e);
        }
    }
}