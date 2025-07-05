package dev.automata.automata.service;

import dev.automata.automata.dto.AutomationCache;
import dev.automata.automata.dto.LiveEvent;
import dev.automata.automata.model.Automation;
import dev.automata.automata.model.AutomationDetail;
import dev.automata.automata.model.DeviceActionState;
import dev.automata.automata.modules.Wled;
import dev.automata.automata.repository.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AutomationService {

    private final DeviceRepository deviceRepository;
    private final AutomationRepository automationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisService redisService;
    private final MainService mainService;
    private final NotificationService notificationService;
    private final AutomationDetailRepository automationDetailRepository;
    private final DeviceActionStateRepository deviceActionStateRepository;

    private final ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();

    @PostConstruct
    public void init() {
        taskScheduler.setPoolSize(10);
        taskScheduler.initialize();
    }

    public List<Automation> findAll() {
        return automationRepository.findAll();
    }

    public Automation create(Automation action) {
        return automationRepository.save(action);
    }

    public String handleAction(String deviceId, Map<String, Object> payload, String deviceType) {
        if ("WLED".equals(deviceType)) {
            var result = handleWLED(deviceId, payload);
            notifyBasedOnResult(result);
            return result;
        }
        System.err.println("Received action");
        System.err.println("Device Type: " + deviceType);
        System.err.println("Payload: " + payload);

        if ("System".equals(deviceType)) {
            var key = payload.get("key").toString();
            var data = payload.get(key).toString();
            if (payload.get("key").equals("alert")) {
                notificationService.sendNotification("", data);
            }
            if (payload.get("key").equals("app_notify")) {
                notificationService.sendNotify("Automation", data, "low");
            }
            return "success";
        }

        if ("reboot".equals(payload.get("key"))) {
            return rebootDevice(deviceId);
        }

        if (payload.containsKey("direct")) {
            sendDirectAction(deviceId, payload);
            return "No saved action found but sent directly";
        }

        var automations = automationRepository.findByTrigger_DeviceId(deviceId);
        automations.forEach(a -> checkAndExecuteSingleAutomation(a, payload));

        notificationService.sendNotification("Action applied", "success");
        return "Action successfully sent!";
    }

    private void notifyBasedOnResult(String result) {
        if ("Success".equals(result)) {
            notificationService.sendNotification("Action applied", "success");
        } else {
            notificationService.sendNotification("Action failed", "error");
        }
    }

    private String rebootDevice(String deviceId) {
        var device = deviceRepository.findById(deviceId).orElse(null);
        if (device == null) return "Device not found";

        try {
            var res = new RestTemplate().getForObject("http://" + device.getAccessUrl() + "/restart", String.class);
            System.err.println(res);
        } catch (Exception e) {
            System.err.println(e.getMessage());
        }
        return "Rebooting device";
    }

    private void sendDirectAction(String deviceId, Map<String, Object> payload) {
        var map = new HashMap<String, Object>();
        var key = payload.get("key").toString();
        map.put(key, payload.get(key));
        map.put("key", key);
        messagingTemplate.convertAndSend("/topic/action/" + deviceId, map);
        notificationService.sendNotification("Action applied", "success");
    }

    private String handleWLED(String deviceId, Map<String, Object> payload) {
        var device = deviceRepository.findById(deviceId).orElse(null);
        var deviceState = deviceActionStateRepository.findById(deviceId).orElse(null);

        if (device == null) return "Not found";

        try {
            var wled = new Wled(device.getAccessUrl());
            var key = payload.get("key").toString();
            String result = switch (key) {
                case "bright" -> wled.setBrightness(Integer.parseInt(payload.get(key).toString()));
                case "onOff" -> wled.powerOnOff(true);
                case "preset" -> wled.setPresets(Integer.parseInt(payload.get(key).toString()));
                default -> "No action found for key: " + key;
            };
            var data = wled.getInfo(deviceId, deviceState);
            mainService.saveData(deviceId, data);
            messagingTemplate.convertAndSend("/topic/data", Map.of("deviceId", deviceId, "data", data));
            return result;
        } catch (Exception e) {
            System.err.println(e);
            return "Error";
        }
    }

    public void checkAndExecuteAutomations() {
        automationRepository.findAll().forEach(automation -> {
            var payload = mainService.getLastData(automation.getTrigger().getDeviceId());
            if (isTriggered(automation, payload)) {
                notificationService.sendNotification("Executing automations: " + automation.getName(), "high");
                executeActions(automation);
            }
        });
    }

    public void checkAndExecuteSingleAutomation(Automation automation, Map<String, Object> payload) {
        if (payload != null && isTriggered(automation, payload) && automation.getIsEnabled()) {
            automation.setIsActive(true);
            System.err.println("Executing automations: " + automation.getName());
            notificationService.sendNotification("Executing automations: " + automation.getName(), "high");
            executeActions(automation);
        } else {
//            System.err.println("Automation Condition Not Matched "+automation.getName());
            automation.setIsActive(false);
        }
    }

    private boolean isTriggered(Automation automation, Map<String, Object> payload) {
        if ("time".equals(automation.getTrigger().getType())) {
            return isCurrentTime(automation.getConditions().get(0).getTime());
        }

        String key = automation.getTrigger().getKey();
        if (!payload.containsKey(key)) return false;

        var condition = automation.getConditions().getFirst();
        var value = payload.get(key).toString();
        var numericValue = Double.parseDouble(value);

        if ("state".equals(automation.getTrigger().getType()) || "periodic".equals(automation.getTrigger().getType())) {
            if (condition.getIsExact()) {
                return value.equals(condition.getValue());
            }
            double above = Double.parseDouble(condition.getAbove());
            double below = Double.parseDouble(condition.getBelow());
            return numericValue > above && numericValue < below;
        }
        return false;
    }
    private boolean isCurrentTime(String triggerTime) {
        // Set the zone to IST
        ZoneId istZone = ZoneId.of("Asia/Kolkata");

        // Get current time in IST
        ZonedDateTime now = ZonedDateTime.now(istZone);
        LocalTime current = now.toLocalTime();

        // Parse the trigger time assuming it's in ISO_OFFSET_DATE_TIME format
        ZonedDateTime targetDateTime = ZonedDateTime.parse(triggerTime, DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        // Convert the parsed time to IST
        LocalTime target = targetDateTime.withZoneSameInstant(istZone).toLocalTime();

        // Check if the target time is within 2 minutes of the current IST time
        return Math.abs(ChronoUnit.MINUTES.between(target, current)) <= 2;
    }

    private void executeActions(Automation automation) {
        for (Automation.Action action : automation.getActions()) {
            if (Boolean.FALSE.equals(action.getIsEnabled())) continue;

            var payload = Map.of(
                    action.getKey(), action.getData(),
                    "key", action.getKey()
            );

            System.err.println(action);
            if ("System".equals(action.getName())) {
                if (action.getKey().equals("alert")) {
                    notificationService.sendAlert("Alert: " + action.getData().toUpperCase(Locale.ROOT), action.getData());
                }
            } else if ("WLED".equals(mainService.getDevice(action.getDeviceId()).getType())) {
                handleWLED(action.getDeviceId(), new HashMap<>(payload));
            } else {
                messagingTemplate.convertAndSend("/topic/action/" + action.getDeviceId(), payload);
            }
        }
    }

//    @Scheduled(fixedRate = 60*1000)
//    public void test(){
//        notificationService.sendAlert("Alert: ", "critical");
//    }

    @EventListener
    public void onCustomEvent(LiveEvent event) {
        var payload = event.getPayload();
        var deviceId = payload.get("device_id").toString();
        var automationCache = redisService.getAutomationCache(deviceId);
        if (automationCache != null && !payload.isEmpty()) {
            checkAndExecuteSingleAutomation(automationCache.getAutomation(), payload);
        }
    }

    @Scheduled(fixedRate = 20000)
    private void triggerPeriodicAutomations() {
        automationRepository.findByIsEnabledTrue().forEach(a ->
                checkAndExecuteSingleAutomation(a, mainService.getLastData(a.getTrigger().getDeviceId())));
    }

    @Scheduled(fixedRate = 300000)
    private void updateRedisStorage() {
        redisService.clearAutomationCache();
        automationRepository.findAll().forEach(a -> {
            redisService.setAutomationCache(a.getId(), AutomationCache.builder()
                    .id(a.getId())
                    .automation(a)
                    .isActive(false)
                    .lastUpdate(new Date())
                    .build());
        });
    }

    public String saveAutomationDetail(AutomationDetail detail) {
        var automationBuilder = Automation.builder()
                .isEnabled(true)
                .isActive(false);

        if (detail.getId() != null && !detail.getId().isEmpty()) automationBuilder.id(detail.getId());

        detail.getNodes().stream().filter(n -> n.getData().getTriggerData() != null).findFirst().ifPresent(triggerNode -> {
            var tData = triggerNode.getData().getTriggerData();
            automationBuilder.trigger(new Automation.Trigger(tData.getDeviceId(), tData.getType(), tData.getValue(), tData.getKey(), tData.getName()));
            automationBuilder.name(tData.getName());
        });

        var actions = detail.getNodes().stream()
                .filter(n -> n.getData().getActionData() != null)
                .map(n -> {
                    var a = n.getData().getActionData();
                    return new Automation.Action(a.getKey(), a.getDeviceId(), a.getData(), a.getName(), a.getIsEnabled());
                }).toList();

        automationBuilder.actions(actions);

        detail.getNodes().stream().filter(n -> n.getData().getConditionData() != null).findFirst().ifPresent(conditionNode -> {
            var c = conditionNode.getData().getConditionData();
            automationBuilder.conditions(List.of(new Automation.Condition(c.getCondition(), c.getValueType(), c.getAbove(), c.getBelow(), c.getValue(), c.getTime(), c.getIsExact())));
        });

        var automation = automationBuilder.build();
        var saved = automationRepository.save(automation);
        detail.setId(saved.getId());
        automationDetailRepository.save(detail);

        notificationService.sendNotification("Automation saved successfully", "success");
        return "success";
    }

    public List<Automation> getActions() {
        return automationRepository.findAll();
    }

    public AutomationDetail getAutomationDetail(String id) {
        return automationDetailRepository.findById(id).orElse(null);
    }

    public String disableAutomation(String id, Boolean enabled) {
        var automation = automationRepository.findById(id).orElse(null);
        if (automation != null) {
            automation.setIsEnabled(enabled);
            automationRepository.save(automation);
            notificationService.sendNotification("Automation updated", "success");
        }
        return "success";
    }

    public String ackAction(String deviceId, Map<String, Object> payload) {
        if (payload.containsKey("actionAck")) {
            messagingTemplate.convertAndSend("/topic/data", Map.of("deviceId", deviceId, "ack", payload));
        }
        return "success";
    }

    public String rebootAllDevices() {
        var devices = deviceRepository.findAll();
        RestTemplate restTemplate = new RestTemplate();
        devices.forEach(device -> {
            var map = Map.of("deviceId", device.getId(), "reboot", true, "key", "reboot");
            messagingTemplate.convertAndSend("/topic/action/" + device.getId(), map);
            try {
                var res = restTemplate.getForObject("http://" + device.getAccessUrl() + "/restart", String.class);
                System.err.println(res);
            } catch (Exception e) {
                System.err.println(e.getMessage());
            }
        });
        notificationService.sendNotification("Rebooting All Devices", "success");
        return "success";
    }
}
