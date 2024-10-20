package dev.automata.automata.service;

import dev.automata.automata.model.Actions;
import dev.automata.automata.model.Device;
import dev.automata.automata.repository.ActionRepository;
import dev.automata.automata.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;

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

    public String create(Actions action) {
        actionRepository.save(action);
        return action.getId();
    }

    public String handleAction(String deviceId) {
        Actions action = actionRepository.findByProducerDeviceId(deviceId);
        if (action == null) {
            return "No action found!";
        }
        var map = new HashMap<String, Object>();
        if (evaluateCondition(action)){
            map.put(action.getConsumerKey(), action.getValueNegativeC());
        }else{
            map.put(action.getConsumerKey(), action.getValueNegativeC());
        }
        messagingTemplate.convertAndSend("/topic/action/"+action.getConsumerDeviceId(), map);
        return "Action successfully sent!";
    }

    public Boolean evaluateCondition(Actions action) {
        // Return value based on condition evaluation
        return switch (action.getProducerValueDataType().toLowerCase()) {
            case "int" -> compareInts(action.getDefaultValue(), action.getProducerValue(), action.getCondition());
            case "float" -> compareFloats(action.getDefaultValue(), action.getProducerValue(), action.getCondition());
            case "string" -> compareStrings(action.getDefaultValue(), action.getProducerValue(), action.getCondition());
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


//    @Scheduled(fixedRate = 20000)
//    public void refreshDevices() {
//
//    }
}
