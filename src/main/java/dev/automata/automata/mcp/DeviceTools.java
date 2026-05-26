package dev.automata.automata.mcp;

import dev.automata.automata.model.Device;
import dev.automata.automata.service.AutomationService;
import dev.automata.automata.service.MainService;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DeviceTools {

    private final MainService mainService;
    private final AutomationService automationService;

    @Tool(description = "List all registered IoT devices with their name, type, category, host, status and last data")
    public List<Device> listDevices() {
        return mainService.getAllDevice();
    }

    @Tool(description = "Get a specific IoT device by its ID including all attributes and last known data")
    public Device getDeviceById(
            @ToolParam(description = "The unique device ID") String deviceId
    ) {
        return mainService.getDevice(deviceId);
    }

    @Tool(description = "Get the current sensor data and attribute values for a device")
    public Map<String, Object> getDeviceData(
            @ToolParam(description = "The unique device ID") String deviceId
    ) {
        return mainService.getLastData(deviceId);
    }

    @Tool(description = "Send a command or action to an IoT device. The payload must contain the key and value to set on the device.")
    public String sendDeviceCommand(
            @ToolParam(description = "The unique device ID to send the command to") String deviceId,
            @ToolParam(description = "The device type (e.g. sensor, actuator, wled)") String deviceType,
            @ToolParam(description = "The attribute key to set (e.g. 'power', 'brightness', 'color')") String key,
            @ToolParam(description = "The value to set for the attribute") String value
    ) {
        Map<String, Object> payload = Map.of(
                "device_id", deviceId,
                "key", key,
                "value", value
        );
        return automationService.handleAction(deviceId, payload, deviceType, "mcp");
    }
}
