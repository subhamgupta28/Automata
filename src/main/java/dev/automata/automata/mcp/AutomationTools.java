package dev.automata.automata.mcp;

import dev.automata.automata.model.Automation;
import dev.automata.automata.service.AutomationService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AutomationTools {

    private final AutomationService automationService;

    @Tool(description = "List all automation rules including their name, enabled state, trigger device, conditions, and actions")
    public List<Automation> listAutomations() {
        return automationService.findAll();
    }

    @Tool(description = "Enable or disable a specific automation rule by its ID")
    public String setAutomationEnabled(
            @ToolParam(description = "The unique automation rule ID") String automationId,
            @ToolParam(description = "true to enable the automation, false to disable it") Boolean enabled
    ) {
        return automationService.disableAutomation(automationId, enabled);
    }

    @Tool(description = "Manually trigger an automation rule by sending a simulated event to its trigger device")
    public String triggerAutomation(
            @ToolParam(description = "The unique automation rule ID to trigger") String automationId
    ) {
        Automation automation = automationService.findAll().stream()
                .filter(a -> a.getId().equals(automationId))
                .findFirst()
                .orElse(null);

        if (automation == null) {
            return "Automation not found: " + automationId;
        }
        if (automation.getTrigger() == null || automation.getTrigger().getDeviceId() == null) {
            return "Automation has no trigger device configured";
        }

        String deviceId = automation.getTrigger().getDeviceId();
        java.util.Map<String, Object> payload = java.util.Map.of(
                "device_id", deviceId,
                "key", automation.getTrigger().getKey() != null ? automation.getTrigger().getKey() : "trigger",
                "value", automation.getTrigger().getValue() != null ? automation.getTrigger().getValue() : "1"
        );
        return automationService.handleAction(deviceId, payload, "", "mcp");
    }
}
