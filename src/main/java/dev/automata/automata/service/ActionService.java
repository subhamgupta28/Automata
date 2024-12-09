package dev.automata.automata.service;

import dev.automata.automata.model.Actions;
import dev.automata.automata.modules.Wled;
import dev.automata.automata.repository.ActionRepository;
import dev.automata.automata.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ActionService {

    private final DeviceRepository deviceRepository;
    private final ActionRepository actionRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public List<Actions> findAll() {
        return actionRepository.findAll();
    }

    public List<Actions> findByDevice(String deviceId) {
        return actionRepository.findByProducerDeviceIdOrConsumerDeviceId(deviceId, deviceId);
    }

    public Actions create(Actions action) {
        return actionRepository.save(action);
    }

    public String handleAction(String deviceId, Map<String, Object> payload, String deviceType) {
        var map = new HashMap<String, Object>();

        if (deviceType.equals("WLED")) {

            return handleWLED(deviceId, payload);
        }

        Actions action = actionRepository.findByProducerDeviceIdAndProducerKey(deviceId, payload.get("key").toString());
        if (action == null || payload.get("direct") != null) {
            map.put(payload.get("key").toString(), payload.get(payload.get("key").toString()).toString());
            System.err.println("direct = " + map);
            messagingTemplate.convertAndSend("/topic/action/" + deviceId, map);
            return "No saved action found but sent directly";
        }
        System.err.println(action);
        String value = payload.get(action.getProducerKey()).toString();

        map.put(action.getConsumerKey(), action.getValueNegativeC());
        messagingTemplate.convertAndSend("/topic/action/" + action.getConsumerDeviceId(), map);
        System.err.println("Action sent!" + map);
        return "Action successfully sent!";
    }

    private String handleWLED(String deviceId, Map<String, Object> payload) {
        var device = deviceRepository.findById(deviceId).orElse(null);
        if (device != null) {
            var wled = new Wled(device.getAccessUrl());
            var key = payload.get("key").toString();
            switch (key) {
                case "bright":
                    return wled.setBrightness(Integer.parseInt(payload.get(key).toString()));
                case "onOff":
                    return wled.powerOnOff(true);
                case "preset":
                    return wled.setPresets(Integer.parseInt(payload.get(key).toString()));

            }
        }
        return "Not found";
    }

    public Boolean evaluateCondition(Actions action, String value) {
        // Return value based on condition evaluation
        return switch (action.getProducerValueDataType().toLowerCase()) {
            case "int" -> compareInts(action.getDefaultValue(), value, action.getCondition());
            case "float" -> compareFloats(action.getDefaultValue(), value, action.getCondition());
            case "string" -> compareStrings(action.getDefaultValue(), value, action.getCondition());
            default -> throw new IllegalArgumentException("Invalid data type: " + action.getProducerValueDataType());
        };
    }

    private boolean compareInts(String defaultValue, String producerValue, String condition) {
        int defaultValueInt = Integer.parseInt(defaultValue);
        int producerValueInt = Integer.parseInt(producerValue);
        return evaluateConditionResult(defaultValueInt, producerValueInt, condition);
    }

    private boolean compareFloats(String defaultValue, String producerValue, String condition) {
        float defaultValueFloat = Float.parseFloat(defaultValue);
        float producerValueFloat = Float.parseFloat(producerValue);
        return evaluateConditionResult(defaultValueFloat, producerValueFloat, condition);
    }

    private boolean compareStrings(String defaultValue, String producerValue, String condition) {
        return evaluateConditionResult(defaultValue, producerValue, condition);
    }

    private <T> boolean evaluateConditionResult(T defaultValue, T producerValue, String condition) {
        return switch (condition) {
            case ">" -> ((Comparable<T>) defaultValue).compareTo(producerValue) > 0;
            case "<" -> ((Comparable<T>) defaultValue).compareTo(producerValue) < 0;
            case "=" -> defaultValue.equals(producerValue);
            case "!=" -> !defaultValue.equals(producerValue);
            default -> throw new IllegalArgumentException("Invalid condition: " + condition);
        };
    }

    public List<Actions> getActions() {
        return actionRepository.findAll();
    }


//    @Scheduled(fixedRate = 20000)
//    public void refreshDevices() {
//
//    }
}
