package dev.automata.automata.service;

import dev.automata.automata.dto.AutomationCache;
import dev.automata.automata.dto.LiveEvent;
import dev.automata.automata.model.Automation;
import dev.automata.automata.modules.Wled;
import dev.automata.automata.repository.AutomationRepository;
import dev.automata.automata.repository.DeviceRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AutomationService {

    private final DeviceRepository deviceRepository;
    private final AutomationRepository automationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisService redisService;
    private final MainService mainService;

    private final ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();

    @PostConstruct
    public void init() {
        taskScheduler.setPoolSize(10);  // Set the pool size for concurrent tasks
        taskScheduler.initialize();
    }


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
        var automationCache = redisService.getAutomationCache(deviceId);
        if (automationCache != null && !payload.isEmpty()) {
//            System.err.println("Found Automation for " + deviceId);
            checkAndExecuteSingleAutomation(automationCache.getAutomation(), payload);
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
            var payload = mainService.getLastData(automation.getTrigger().getDeviceId());
            if (isTriggered(automation, payload)) {
                executeActions(automation);
            }
        }
    }

    public void checkAndExecuteSingleAutomation(Automation automation, Map<String, Object> payload) {
//        System.err.println(payload);
        if (payload!=null && isTriggered(automation, payload)) {
            executeActions(automation);
        } else {
            System.err.println("No state match for payload: " + payload);
        }
    }

    private boolean isTriggered(Automation automation, Map<String, Object> payload) {
        // Check trigger conditions (e.g., time-based, state change, etc.)
        String key = automation.getTrigger().getKey();
        var condition = automation.getConditions().getFirst();
        var value = payload.get(key).toString();
        var parseValue = Double.parseDouble(value);//53
        if ("time".equals(automation.getTrigger().getType())) {
            String triggerTime = automation.getTrigger().getValue();
            return isCurrentTime(triggerTime);
        }
        if ("state".equals(automation.getTrigger().getType())) {
            if (condition.getIsExact()) {
                var expectedValue = condition.getValue();
                return value.equals(expectedValue);
            } else {
                var above = Double.parseDouble(condition.getAbove());//0
                var below = Double.parseDouble(condition.getBelow());//60
                System.err.println(value + " " + above + " " + below);
                return parseValue > above && parseValue < below;
            }
        }
        if ("periodic".equals(automation.getTrigger().getType())) {

            var above = Double.parseDouble(condition.getAbove());//0
            var below = Double.parseDouble(condition.getBelow());//60
            System.err.println(value + " " + above + " " + below);
            return parseValue > above && parseValue < below;
        }
        return false;
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
            var device = mainService.getDevice(action.getDeviceId());
            System.err.println(action);


            var payload = new HashMap<String, Object>();
            var data = action.getData();
            switch (data) {
                case "true":
                    payload.put(action.getKey(), true);
                    break;
                case "false":
                    payload.put(action.getKey(), false);
                default:
                    payload.put(action.getKey(), action.getData());
            }

            payload.put("key", action.getKey());

            if (device.getType().equals("WLED")) {
                handleWLED(action.getDeviceId(), payload);
            } else {
                messagingTemplate.convertAndSend(
                        "/topic/action/" + action.getDeviceId(), payload
                );
            }
            // You would call the actual services here, for example, turning on lights, sending notifications, etc.
        }
    }


    @EventListener
    public void onCustomEvent(LiveEvent event) {
        var payload = event.getPayload();
        System.err.println("Custom event received with message: " + payload);
        String deviceId = payload.get("device_id").toString();

        var automationCache = redisService.getAutomationCache(deviceId);
        if (automationCache != null && !payload.isEmpty()) {
//            System.err.println("Found Automation for " + deviceId);
            checkAndExecuteSingleAutomation(automationCache.getAutomation(), payload);
        }


    }

    private void registerTask(String taskName, String cronExpression) {
        ScheduledTaskRegistrar registrar = new ScheduledTaskRegistrar();
        registrar.setTaskScheduler(taskScheduler);
        registrar.addCronTask(() -> runTask(taskName), cronExpression);
        registrar.afterPropertiesSet();
    }

    // This is the method that gets called when the scheduled task is triggered
    private void runTask(String taskName) {
        System.out.println("Running task: " + taskName);
        // Your logic for the task here
    }

    @Scheduled(cron = "0 30 7 * * ?") // Run at 7:30 AM every day
    private void triggerAutomations() {
//        checkAndExecuteAutomations();
    }

    @Scheduled(fixedRate = 30000)
    private void triggerPeriodicAutomations() {
        var automations = automationRepository.findAll();
        automations.forEach(a -> {
            var lastData = mainService.getLastData(a.getTrigger().getDeviceId());
            checkAndExecuteSingleAutomation(a, lastData);
        });
    }

    @Scheduled(fixedRate = 60000 * 5) // Every 5 min
    private void updateRedisStorage() {
        System.err.println("Updating redis");


        var automations = automationRepository.findAll();
        automations.forEach(a -> {
            System.err.println(a);
            var automationCache = AutomationCache.builder()
                    .id(a.getId())
                    .automation(a)
                    .isActive(false)
                    .lastUpdate(new Date())
                    .build();
            redisService.setAutomationCache(a.getTrigger().getDeviceId(), automationCache);


        });

    }
}
