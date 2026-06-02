package dev.automata.automata.mcp;

import dev.automata.automata.model.Device;
import dev.automata.automata.service.AutomationService;
import dev.automata.automata.service.MainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceTools {

    private final MainService mainService;
    private final AutomationService automationService;

    @Tool(description = "List all registered IoT devices with their name, type, category, host, status and last data")
    public List<Device> listDevices() {
        log.info("DeviceTools: listDevices");
        return mainService.getAllDevice();
    }

    @Tool(description = "Get a specific IoT device by its ID including all attributes and last known data")
    public Device getDeviceById(
            @ToolParam(description = "The unique device ID") String deviceId
    ) {
        log.info("DeviceTools: getDeviceById deviceId [{}]", deviceId);
        return mainService.getDevice(deviceId);
    }

    @Tool(description = "Get the current sensor data and attribute values for a device")
    public Map<String, Object> getDeviceData(
            @ToolParam(description = "The unique device ID") String deviceId
    ) {
        log.info("DeviceTools: getDeviceData deviceId [{}]", deviceId);
        return mainService.getLastData(deviceId);
    }

    /**
     * Sends a direct command to a device, bypassing automation routing.
     * <p>
     * AutomationService.handleAction() checks for payload.containsKey("direct") == true
     * and calls dispatcher.dispatchDirect() immediately — no automation lookup occurs.
     * <p>
     * Payload shape expected by dispatchDirect (mirrors what handleAction builds):
     * { "key": "<key>", "<key>": <value>, "direct": true, "deviceId": "<deviceId>" }
     * <p>
     * Examples:
     * Turn on ring light  → key="power",      value="1"
     * Set brightness      → key="brightness",  value="80"
     * Set WLED color      → key="color",       value="#FF0000"
     * Set fan speed       → key="speed",       value="3"
     */
    @Tool(description = """
            Send a direct command to an IoT device, bypassing automation rules.
            Use this to directly control device attributes such as:
            - Turning a ring light on/off (key=power, value=1 or 0)
            - Setting brightness (key=brightness, value=0-100)
            - Setting color on a WLED strip (key=color, value=#RRGGBB)
            - Controlling fan speed (key=speed, value=0-5)
            The command is dispatched immediately without triggering any automation logic.
            """)
    public String sendDeviceCommand(
            @ToolParam(description = "The unique device ID to send the command to") String deviceId,
            @ToolParam(description = "The device type (e.g. sensor, actuator, WLED)") String deviceType,
            @ToolParam(description = "The attribute key to set (e.g. 'power', 'brightness', 'color', 'speed')") String key,
            @ToolParam(description = "The value to set for the attribute (e.g. '1', '80', '#FF0000')") String value
    ) {
        // Shape matches the "direct" branch in AutomationService.handleAction():
        //   if (payload.containsKey("direct") && Boolean.parseBoolean(...)) {
        //       dispatcher.dispatchDirect(deviceId, payload);
        //       return "Direct action sent";
        //   }
        Map<String, Object> payload = new HashMap<>();
        payload.put("key", key);
        payload.put(key, value);          // dispatcher expects key→value entry (e.g. "power"→"1")
        payload.put("direct", true);
        payload.put("deviceId", deviceId);
        log.info("DeviceTools: sendDeviceCommand payload [{}]", payload);
        return automationService.handleAction(deviceId, payload, deviceType, "mcp");
    }

    /**
     * Convenience wrapper for WLED devices specifically.
     * AutomationService routes WLED type to handleWLED() before any other logic,
     * so the direct flag is not needed — just ensure deviceType="WLED".
     */
    @Tool(description = """
            Send a command specifically to a WLED LED strip or ring light.
            Common keys: 'onOff' (false/true), 'bright' (0-255), 'color' (#RRGGBB),
            'effect' (effect index), 'preset' (preset index).
            The device type is automatically set to WLED.
            """)
    public String sendWledCommand(
            @ToolParam(description = "The unique WLED device ID") String deviceId,
            @ToolParam(description = "The attribute key (e.g. 'onOff', 'bright', 'color', 'effect', 'preset')") String key,
            @ToolParam(description = "The value to set") String value
    ) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("key", key);
        payload.put(key, value);
        payload.put("deviceId", deviceId);

        log.info("DeviceTools: sendWledCommand payload [{}]", payload);
        // "WLED" type is intercepted at the top of handleAction() → handleWLED()
        return automationService.handleAction(deviceId, payload, "WLED", "mcp");
    }
}
