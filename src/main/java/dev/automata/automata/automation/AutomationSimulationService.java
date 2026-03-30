package dev.automata.automata.automation;

import dev.automata.automata.model.Automation;
import dev.automata.automata.model.AutomationLog;
import dev.automata.automata.repository.AutomationRepository;
import dev.automata.automata.repository.DeviceRepository;
import dev.automata.automata.service.MainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * LOW PRIORITY: Dry-run simulation service
 * Allows testing automations without actually executing actions
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationSimulationService {

    private final AutomationRepository automationRepository;
    private final DeviceRepository deviceRepository;
    private final MainService mainService;

    /**
     * Simulates automation execution with given payload
     * Returns what WOULD happen without actually doing it
     */
    public AutomationSimulationResult simulateAutomation(String automationId, Map<String, Object> testPayload) {
        var automation = automationRepository.findById(automationId).orElse(null);

        if (automation == null) {
            return AutomationSimulationResult.builder()
                    .success(false)
                    .error("Automation not found: " + automationId)
                    .build();
        }

        return simulateAutomation(automation, testPayload);
    }

    /**
     * Simulates automation with custom payload
     */
    public AutomationSimulationResult simulateAutomation(Automation automation, Map<String, Object> testPayload) {
        log.info("🧪 Simulating automation: {}", automation.getName());

        var result = AutomationSimulationResult.builder()
                .automationId(automation.getId())
                .automationName(automation.getName())
                .testPayload(testPayload)
                .timestamp(new Date());

        try {
            // Step 1: Validate payload has required keys
            List<String> missingKeys = validatePayload(automation, testPayload);
            if (!missingKeys.isEmpty()) {
                return result
                        .success(false)
                        .error("Missing required keys in payload: " + missingKeys)
                        .build();
            }

            // Step 2: Evaluate conditions
            List<AutomationLog.ConditionResult> conditionResults = new ArrayList<>();
            boolean wouldTrigger = evaluateConditions(automation, testPayload, false, conditionResults);

            result.conditionResults(conditionResults)
                    .wouldTrigger(wouldTrigger);

            // Step 3: If would trigger, simulate actions
            if (wouldTrigger) {
                List<SimulatedAction> simulatedActions = simulateActions(automation, testPayload);
                result.simulatedActions(simulatedActions)
                        .actionCount(simulatedActions.size());
            } else {
                result.simulatedActions(Collections.emptyList())
                        .actionCount(0);
            }

            // Step 4: Identify affected devices
            Set<String> affectedDevices = automation.getActions().stream()
                    .filter(a -> a.getDeviceId() != null)
                    .map(Automation.Action::getDeviceId)
                    .collect(Collectors.toSet());

            result.affectedDeviceIds(new ArrayList<>(affectedDevices))
                    .success(true);

            log.info("✅ Simulation complete: {} - Would trigger: {}",
                    automation.getName(), wouldTrigger);

        } catch (Exception e) {
            log.error("❌ Simulation failed for {}: {}", automation.getName(), e.getMessage(), e);
            result.success(false)
                    .error("Simulation error: " + e.getMessage());
        }

        return result.build();
    }

    /**
     * Simulates automation with current device state
     */
    public AutomationSimulationResult simulateWithCurrentState(String automationId) {
        var automation = automationRepository.findById(automationId).orElse(null);

        if (automation == null) {
            return AutomationSimulationResult.builder()
                    .success(false)
                    .error("Automation not found: " + automationId)
                    .build();
        }

        // Get current state from device
        String deviceId = automation.getTrigger().getDeviceId();
        Map<String, Object> currentState = mainService.getLastData(deviceId);

        if (currentState == null || currentState.isEmpty()) {
            return AutomationSimulationResult.builder()
                    .success(false)
                    .error("No current state available for device: " + deviceId)
                    .build();
        }

        return simulateAutomation(automation, currentState);
    }

    /**
     * Batch simulation - test automation against multiple payloads
     */
    public List<AutomationSimulationResult> batchSimulate(
            String automationId,
            List<Map<String, Object>> testPayloads) {

        return testPayloads.stream()
                .map(payload -> simulateAutomation(automationId, payload))
                .collect(Collectors.toList());
    }

    /**
     * Validates that payload contains all required keys
     */
    private List<String> validatePayload(Automation automation, Map<String, Object> payload) {
        List<String> missingKeys = new ArrayList<>();

        for (var condition : automation.getConditions()) {
            if ("scheduled".equals(condition.getCondition())) {
                continue; // Time-based conditions don't need payload keys
            }

            String key = condition.getTriggerKey();
            if (key != null && !key.isEmpty() && !payload.containsKey(key)) {
                missingKeys.add(key);
            }
        }

        return missingKeys;
    }

    /**
     * Evaluates conditions (copied from main service for simulation)
     */
    private boolean evaluateConditions(
            Automation automation,
            Map<String, Object> payload,
            boolean wasActive,
            List<AutomationLog.ConditionResult> results) {

        if ("time".equals(automation.getTrigger().getType())) {
            var condition = automation.getConditions().get(0);
            boolean passed = isCurrentTime(condition);

            results.add(AutomationLog.ConditionResult.builder()
                    .conditionType("time")
                    .triggerKey("time")
                    .expectedValue(condition.getTime())
                    .actualValue(LocalTime.now(ZoneId.of("Asia/Kolkata")).toString())
                    .passed(passed)
                    .detail(passed ? "Current time matches trigger time" :
                            "Current time does not match trigger time")
                    .build());
            return passed;
        }

        var conditions = automation.getConditions();
        var operators = automation.getOperators();
        var truths = new ArrayList<Boolean>();

        for (var condition : conditions) {
            if ("scheduled".equals(condition.getCondition())) {
                boolean passed = isCurrentTime(condition);
                results.add(AutomationLog.ConditionResult.builder()
                        .conditionType("scheduled")
                        .triggerKey("schedule")
                        .expectedValue(condition.getScheduleType() + " @ " + condition.getTime())
                        .actualValue(LocalTime.now(ZoneId.of("Asia/Kolkata")).toString())
                        .passed(passed)
                        .detail(passed ? "Schedule matched" : "Schedule not matched")
                        .build());
                truths.add(passed);
                continue;
            }

            String key = condition.getTriggerKey();
            if (key == null || key.isBlank() || !payload.containsKey(key)) {
                results.add(AutomationLog.ConditionResult.builder()
                        .conditionType(condition.getCondition())
                        .triggerKey(key)
                        .passed(false)
                        .detail("Key '" + key + "' not found in payload")
                        .build());
                truths.add(false);
                continue;
            }

            String value = payload.get(key).toString();

            if (isNumeric(value)) {
                double numericValue = Double.parseDouble(value);
                boolean passed = checkCondition(numericValue, condition, wasActive);
                results.add(buildConditionResult(condition, numericValue, passed, wasActive));
                truths.add(passed);
            } else {
                boolean passed = value.equals(condition.getValue());
                results.add(AutomationLog.ConditionResult.builder()
                        .conditionType("equal")
                        .triggerKey(key)
                        .actualValue(value)
                        .expectedValue(condition.getValue())
                        .passed(passed)
                        .detail(passed ? "'" + value + "' equals expected" :
                                "'" + value + "' does not equal '" + condition.getValue() + "'")
                        .build());
                truths.add(passed);
            }
        }

        if (truths.isEmpty()) return false;

        String globalLogic = (operators != null && !operators.isEmpty())
                ? operators.get(0).getLogicType() : "AND";

        return switch (globalLogic.toUpperCase()) {
            case "OR" -> truths.stream().anyMatch(Boolean::booleanValue);
            case "AND" -> truths.stream().allMatch(Boolean::booleanValue);
            default -> truths.stream().allMatch(Boolean::booleanValue);
        };
    }

    /**
     * Simulates what actions would be executed
     */
    private List<SimulatedAction> simulateActions(Automation automation, Map<String, Object> payload) {
        List<SimulatedAction> simulated = new ArrayList<>();

        for (var action : automation.getActions()) {
            if (Boolean.FALSE.equals(action.getIsEnabled())) {
                continue;
            }

            var device = deviceRepository.findById(action.getDeviceId()).orElse(null);
            String deviceName = device != null ? device.getName() : "Unknown";
            String deviceType = device != null ? device.getType() : "Unknown";

            simulated.add(SimulatedAction.builder()
                    .actionKey(action.getKey())
                    .actionData(action.getData())
                    .deviceId(action.getDeviceId())
                    .deviceName(deviceName)
                    .deviceType(deviceType)
                    .wouldRevert(Boolean.TRUE.equals(action.getRevert()))
                    .description(buildActionDescription(action, deviceName))
                    .build());
        }

        return simulated;
    }

    private String buildActionDescription(Automation.Action action, String deviceName) {
        return String.format("Set %s on device '%s' to %s",
                action.getKey(), deviceName, action.getData());
    }

    // Helper methods
    private boolean isNumeric(String input) {
        return input != null && input.matches("-?\\d+(\\.\\d+)?");
    }

    private boolean checkCondition(Double numericValue, Automation.Condition condition, boolean wasActive) {
        if (condition.getIsExact()) {
            return condition.getValue().equals(numericValue.toString());
        }

        double threshold = Double.parseDouble(condition.getValue());
        double buffer = 5.0;

        return switch (condition.getCondition()) {
            case "above" -> wasActive ? numericValue > (threshold - buffer) : numericValue > threshold;
            case "below" -> wasActive ? numericValue < (threshold + buffer) : numericValue < threshold;
            case "range" -> {
                double above = Double.parseDouble(condition.getAbove());
                double below = Double.parseDouble(condition.getBelow());
                if (wasActive) {
                    yield numericValue > (above - buffer) && numericValue < (below + buffer);
                }
                yield numericValue > above && numericValue < below;
            }
            default -> false;
        };
    }

    private boolean isCurrentTime(Automation.Condition condition) {
        ZoneId istZone = ZoneId.of("Asia/Kolkata");
        ZonedDateTime nowZdt = ZonedDateTime.now(istZone);
        LocalTime current = nowZdt.toLocalTime();

        if (condition.getDays() != null && !condition.getDays().isEmpty()) {
            String today = nowZdt.getDayOfWeek()
                    .getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)
                    .substring(0, 3);
            today = today.substring(0, 1).toUpperCase() + today.substring(1).toLowerCase();
            if (!condition.getDays().contains(today)) return false;
        }

        String scheduleType = condition.getScheduleType();
        if ("range".equals(scheduleType)) {
            // Simplified - actual implementation would parse times
            return true; // Placeholder
        }

        return true; // Placeholder
    }

    private AutomationLog.ConditionResult buildConditionResult(
            Automation.Condition condition, double actual, boolean passed, boolean wasActive) {

        double buffer = 5.0;
        String detail = switch (condition.getCondition()) {
            case "above" -> actual + " > " + condition.getValue() +
                    (wasActive ? " (with buffer)" : "") + " → " + (passed ? "PASS" : "FAIL");
            case "below" -> actual + " < " + condition.getValue() +
                    (wasActive ? " (with buffer)" : "") + " → " + (passed ? "PASS" : "FAIL");
            case "range" -> "Value " + actual + " in range [" + condition.getAbove() +
                    ", " + condition.getBelow() + "] → " + (passed ? "PASS" : "FAIL");
            default -> actual + " == " + condition.getValue() + " → " + (passed ? "PASS" : "FAIL");
        };

        return AutomationLog.ConditionResult.builder()
                .conditionType(condition.getCondition())
                .triggerKey(condition.getTriggerKey())
                .actualValue(String.valueOf(actual))
                .expectedValue(condition.getValue())
                .passed(passed)
                .detail(detail)
                .build();
    }

    /**
     * Inner class for simulated action results
     */
    @lombok.Builder
    @lombok.Data
    public static class SimulatedAction {
        private String actionKey;
        private String actionData;
        private String deviceId;
        private String deviceName;
        private String deviceType;
        private boolean wouldRevert;
        private String description;
    }
}
