package dev.automata.automata.service;

import dev.automata.automata.model.Automation;
import dev.automata.automata.modules.Wled;
import dev.automata.automata.repository.AutomationRepository;
import dev.automata.automata.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AutomationService {

    private final DeviceRepository deviceRepository;
    private final AutomationRepository automationRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public List<Automation> findAll() {
        return automationRepository.findAll();
    }

//    public List<Automation> findByDevice(String deviceId) {
//        return actionRepository.findByProducerDeviceIdOrConsumerDeviceId(deviceId, deviceId);
//    }

    public Automation create(Automation action) {
        return automationRepository.save(action);
    }

    public String handleAction(String deviceId, Map<String, Object> payload, String deviceType) {
        var map = new HashMap<String, Object>();

        if (deviceType.equals("WLED")) {

            return handleWLED(deviceId, payload);
        }

//        Automation action = actionRepository.findByProducerDeviceIdAndProducerKey(deviceId, payload.get("key").toString());
        if (payload.get("direct") != null) {
            map.put(payload.get("key").toString(), payload.get(payload.get("key").toString()).toString());
            System.err.println("direct = " + map);
            messagingTemplate.convertAndSend("/topic/action/" + deviceId, map);
            return "No saved action found but sent directly";
        }
//        System.err.println(action);
//        String value = payload.get(action.getProducerKey()).toString();
//
//        map.put(action.getConsumerKey(), action.getValueNegativeC());
//        messagingTemplate.convertAndSend("/topic/action/" + action.getConsumerDeviceId(), map);
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

    public List<Automation> getActions() {
        return automationRepository.findAll();
    }

    public void checkAndExecuteAutomations() {
        List<Automation> automations = automationRepository.findAll();
        for (Automation automation : automations) {
            if (isTriggered(automation)) {
                executeActions(automation);
            }
        }
    }

    private boolean isTriggered(Automation automation) {
        // Check trigger conditions (e.g., time-based, state change, etc.)
        // This is just an example for time-based triggers
        if ("time".equals(automation.getTrigger().getType())) {
            String triggerTime = automation.getTrigger().getValue();
            return isCurrentTime(triggerTime);
        }
        if ("state".equals(automation.getTrigger().getType())) {
            String deviceId = automation.getTrigger().getDeviceId();
            String expectedState = automation.getTrigger().getValue();
            return isStateMatched(deviceId, expectedState);
        }
        return false;
    }
    private boolean isStateMatched(String entityId, String expectedState) {
        // Retrieve the current state of the entity
        String currentState = "entityStateService.getEntityState(entityId)";

        // Compare the current state with the expected state
        return expectedState.equals(currentState);
    }

    private boolean isCurrentTime(String triggerTime) {
        LocalTime currentTime = LocalTime.now();

        // Parse the trigger time into a LocalTime object
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        LocalTime parsedTriggerTime = LocalTime.parse(triggerTime, formatter);

        // Compare the current time with the trigger time
        return currentTime.equals(parsedTriggerTime);
    }

    private void executeActions(Automation automation) {
        for (Automation.Action action : automation.getActions()) {
            // Execute each action (e.g., call a service, turn on a device)
            System.out.println("Executing action: " + action.getKey());
            System.out.println("Entity: " + action.getDeviceId());
            System.out.println("Data: " + action.getData());
            // You would call the actual services here, for example, turning on lights, sending notifications, etc.
        }
    }

    @Scheduled(fixedRate = 60000) // Every 60 seconds
    private void triggerAutomations() {
        checkAndExecuteAutomations();
    }
}
