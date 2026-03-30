package dev.automata.automata.automation;


import dev.automata.automata.model.AutomationDetail;
import dev.automata.automata.repository.DeviceRepository;
import dev.automata.automata.service.MainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * HIGH PRIORITY 5: Automation Validation Service
 * Validates automation configuration before saving to prevent runtime errors
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationValidationService {

    private final DeviceRepository deviceRepository;
    private final MainService mainService;

    private static final Pattern TIME_PATTERN_24H = Pattern.compile("^([01]\\d|2[0-3]):([0-5]\\d):([0-5]\\d)$");
    private static final Pattern TIME_PATTERN_12H = Pattern.compile("^(0?[1-9]|1[0-2]):([0-5]\\d):([0-5]\\d)\\s?(AM|PM|am|pm)$");

    /**
     * Validates complete automation detail
     *
     * @return List of validation error messages (empty if valid)
     */
    public List<String> validate(AutomationDetail detail) {
        List<String> errors = new ArrayList<>();

        if (detail == null) {
            errors.add("Automation detail is null");
            return errors;
        }

        if (detail.getNodes() == null || detail.getNodes().isEmpty()) {
            errors.add("Automation has no nodes");
            return errors;
        }

        // Validate trigger exists and is valid
        errors.addAll(validateTrigger(detail));

        // Validate conditions
        errors.addAll(validateConditions(detail));

        // Validate actions
        errors.addAll(validateActions(detail));

        // Validate logical consistency
        errors.addAll(validateLogicalConsistency(detail));

        // Detect circular dependencies
        errors.addAll(detectCircularDependencies(detail));

        return errors;
    }

    /**
     * Validates trigger configuration
     */
    private List<String> validateTrigger(AutomationDetail detail) {
        List<String> errors = new ArrayList<>();

        var triggerNode = detail.getNodes().stream()
                .filter(n -> n.getData().getTriggerData() != null)
                .findFirst();

        if (triggerNode.isEmpty()) {
            errors.add("No trigger node found");
            return errors;
        }

        var trigger = triggerNode.get().getData().getTriggerData();

        // Validate trigger name
        if (trigger.getName() == null || trigger.getName().trim().isEmpty()) {
            errors.add("Trigger name is required");
        }

        // Validate trigger type
        if (trigger.getType() == null || trigger.getType().trim().isEmpty()) {
            errors.add("Trigger type is required");
        } else {
            Set<String> validTypes = Set.of("state", "periodic", "time", "manual");
            if (!validTypes.contains(trigger.getType().toLowerCase())) {
                errors.add("Invalid trigger type: " + trigger.getType() +
                        ". Valid types: " + validTypes);
            }
        }

        // Validate device exists
        if (trigger.getDeviceId() != null && !trigger.getDeviceId().isEmpty()) {
            if (!deviceRepository.existsById(trigger.getDeviceId())) {
                errors.add("Trigger device not found: " + trigger.getDeviceId());
            } else {
                // Validate trigger keys exist on device
                if (trigger.getKeys() != null && !trigger.getKeys().isEmpty()) {
                    var device = mainService.getDevice(trigger.getDeviceId());
                    var lastData = mainService.getLastData(trigger.getDeviceId());

                    if (lastData != null) {
                        for (var keyObj : trigger.getKeys()) {
                            String key = keyObj.getKey();
                            if (!lastData.containsKey(key)) {
                                errors.add("Trigger key '" + key + "' not found on device " +
                                        device.getName() + ". Available keys: " +
                                        lastData.keySet());
                            }
                        }
                    }
                }
            }
        }

        return errors;
    }

    /**
     * Validates conditions
     */
    private List<String> validateConditions(AutomationDetail detail) {
        List<String> errors = new ArrayList<>();

        var conditionNodes = detail.getNodes().stream()
                .filter(n -> n.getData().getConditionData() != null)
                .toList();

        if (conditionNodes.isEmpty()) {
            errors.add("No conditions defined - automation will never trigger");
            return errors;
        }

        for (var node : conditionNodes) {
            var condition = node.getData().getConditionData();

            // Validate condition type
            if (condition.getCondition() == null || condition.getCondition().trim().isEmpty()) {
                errors.add("Condition type is required");
                continue;
            }

            String condType = condition.getCondition().toLowerCase();

            switch (condType) {
                case "above", "below" -> {
                    // Validate numeric value
                    if (condition.getValue() == null || condition.getValue().trim().isEmpty()) {
                        errors.add("Condition value is required for '" + condType + "' condition");
                    } else {
                        try {
                            Double.parseDouble(condition.getValue());
                        } catch (NumberFormatException e) {
                            errors.add("Invalid numeric value for '" + condType + "' condition: " +
                                    condition.getValue());
                        }
                    }
                }
                case "range" -> {
                    // Validate range boundaries
                    if (condition.getAbove() == null || condition.getBelow() == null) {
                        errors.add("Range condition requires both 'above' and 'below' values");
                    } else {
                        try {
                            double above = Double.parseDouble(condition.getAbove());
                            double below = Double.parseDouble(condition.getBelow());

                            if (above >= below) {
                                errors.add("Range 'above' value (" + above +
                                        ") must be less than 'below' value (" + below + ")");
                            }
                        } catch (NumberFormatException e) {
                            errors.add("Invalid numeric values for range condition");
                        }
                    }
                }
                case "scheduled" -> {
                    // Validate schedule type
                    if (condition.getScheduleType() == null) {
                        errors.add("Schedule type is required for scheduled condition");
                    } else if ("range".equals(condition.getScheduleType())) {
                        // Validate time range
                        if (!isValidTime(condition.getFromTime())) {
                            errors.add("Invalid 'from' time format: " + condition.getFromTime());
                        }
                        if (!isValidTime(condition.getToTime())) {
                            errors.add("Invalid 'to' time format: " + condition.getToTime());
                        }
                    } else if ("at".equals(condition.getScheduleType())) {
                        // Validate exact time
                        if (!isValidTime(condition.getTime())) {
                            errors.add("Invalid time format: " + condition.getTime());
                        }
                    }

                    // Validate days if specified
                    if (condition.getDays() != null && !condition.getDays().isEmpty()) {
                        Set<String> validDays = Set.of("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun");
                        for (String day : condition.getDays()) {
                            if (!validDays.contains(day)) {
                                errors.add("Invalid day: " + day + ". Valid days: " + validDays);
                            }
                        }
                    }
                }
                case "equal" -> {
                    if (condition.getValue() == null || condition.getValue().trim().isEmpty()) {
                        errors.add("Condition value is required for 'equal' condition");
                    }
                }
                default -> {
                    errors.add("Unknown condition type: " + condType);
                }
            }

            // Validate trigger key if specified
            if (condition.getTriggerKey() != null && !condition.getTriggerKey().isEmpty()) {
                // This will be validated against actual device data at runtime
                log.debug("Condition references trigger key: {}", condition.getTriggerKey());
            }
        }

        return errors;
    }

    /**
     * Validates actions
     */
    private List<String> validateActions(AutomationDetail detail) {
        List<String> errors = new ArrayList<>();

        var actionNodes = detail.getNodes().stream()
                .filter(n -> n.getData().getActionData() != null)
                .toList();

        if (actionNodes.isEmpty()) {
            errors.add("No actions defined - automation will do nothing");
            return errors;
        }

        for (var node : actionNodes) {
            var action = node.getData().getActionData();

            // Validate action has required fields
            if (action.getKey() == null || action.getKey().trim().isEmpty()) {
                errors.add("Action key is required");
            }

            if (action.getData() == null || action.getData().trim().isEmpty()) {
                errors.add("Action data is required");
            }

            // Validate device exists (unless it's a system action)
            if (action.getDeviceId() != null && !action.getDeviceId().isEmpty()) {
                if (!action.getKey().equals("alert") && !action.getKey().equals("app_notify")) {
                    if (!deviceRepository.existsById(action.getDeviceId())) {
                        errors.add("Action device not found: " + action.getDeviceId());
                    }
                }
            } else if (!action.getKey().equals("alert") && !action.getKey().equals("app_notify")) {
                errors.add("Action device ID is required for non-system actions");
            }

            // Validate action is enabled if it has the field
            if (action.getIsEnabled() == null) {
                log.warn("Action isEnabled is null, defaulting to true");
            }
        }

        return errors;
    }

    /**
     * Validates logical consistency of the automation
     */
    private List<String> validateLogicalConsistency(AutomationDetail detail) {
        List<String> errors = new ArrayList<>();

        var operators = detail.getNodes().stream()
                .filter(n -> n.getData().getOperators() != null)
                .toList();

        // If multiple conditions exist, operators should be defined
        var conditionCount = detail.getNodes().stream()
                .filter(n -> n.getData().getConditionData() != null)
                .count();

        if (conditionCount > 1 && operators.isEmpty()) {
            errors.add("Multiple conditions require operator logic (AND/OR)");
        }

        // Validate operator logic types
        for (var node : operators) {
            var operator = node.getData().getOperators();
            if (operator.getLogicType() != null) {
                String logic = operator.getLogicType().toUpperCase();
                if (!logic.equals("AND") && !logic.equals("OR")) {
                    errors.add("Invalid logic type: " + logic + ". Valid types: AND, OR");
                }
            }
        }

        return errors;
    }

    /**
     * Detects circular dependencies in automation triggers
     * (e.g., Automation A triggers device that triggers Automation B which triggers device for A)
     */
    private List<String> detectCircularDependencies(AutomationDetail detail) {
        List<String> errors = new ArrayList<>();

        // This is a simplified check - full implementation would need to traverse the entire automation graph
        var triggerDeviceId = detail.getNodes().stream()
                .filter(n -> n.getData().getTriggerData() != null)
                .findFirst()
                .map(n -> n.getData().getTriggerData().getDeviceId())
                .orElse(null);

        if (triggerDeviceId == null) return errors;

        // Check if any action targets the same device as the trigger
        var actionDeviceIds = detail.getNodes().stream()
                .filter(n -> n.getData().getActionData() != null)
                .map(n -> n.getData().getActionData().getDeviceId())
                .filter(id -> id != null && !id.isEmpty())
                .toList();

        if (actionDeviceIds.contains(triggerDeviceId)) {
            errors.add("WARNING: Automation triggers and acts on the same device (" +
                    triggerDeviceId + "). This may cause infinite loops.");
        }

        return errors;
    }

    /**
     * Validates time format (24-hour or 12-hour with AM/PM)
     */
    private boolean isValidTime(String time) {
        if (time == null || time.trim().isEmpty()) {
            return false;
        }
        return TIME_PATTERN_24H.matcher(time).matches() ||
                TIME_PATTERN_12H.matcher(time).matches();
    }

    /**
     * Quick validation for testing - returns true if valid
     */
    public boolean isValid(AutomationDetail detail) {
        return validate(detail).isEmpty();
    }
}
