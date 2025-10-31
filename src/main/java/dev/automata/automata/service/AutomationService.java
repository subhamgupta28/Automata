package dev.automata.automata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.automata.automata.dto.AutomationCache;
import dev.automata.automata.dto.LiveEvent;
import dev.automata.automata.model.Automation;
import dev.automata.automata.model.AutomationDetail;
import dev.automata.automata.modules.Wled;
import dev.automata.automata.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

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
    private final MessageChannel mqttOutboundChannel;
    private final ObjectMapper objectMapper = new ObjectMapper();

    //    private final ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
//
//    @PostConstruct
//    public void init() {
//        taskScheduler.setPoolSize(10);
//        taskScheduler.initialize();
//    }
    @Scheduled(fixedRate = 10000)
    public void updateWLEDDevices() {
//        var w = new Wled("");
//        mainService.registerDevice(w.newDevice());
        var devices = deviceRepository.findAllByType("WLED");
        devices.forEach(device -> {
            try {
                var deviceId = device.getId();
                var wled = new Wled(device.getAccessUrl());
                var data = wled.getInfo(device.getAccessUrl(), null);
                mainService.saveData(deviceId, data);
                System.err.println("WLED: " + data);
                messagingTemplate.convertAndSend("/topic/data", Map.of("deviceId", deviceId, "data", data));
            } catch (Exception e) {
                System.err.println(e.getMessage());
            }

        });
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

            return "error";
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

        if (payload.containsKey("automation")) {
            var id = payload.get(payload.get("key").toString()).toString();
            automationRepository.findById(id).ifPresent(this::executeActions);
            return "Running automation";
        }
        if (payload.containsValue("master")) {
            var id = payload.get("deviceId").toString();
            var device = deviceRepository.findById(id);
            var key = payload.get("key").toString();
            var value = Integer.parseInt(payload.get("value").toString());
            var screen = payload.get("screen").toString();
            var req = new HashMap<String, Object>();
            req.put("key", key);
            req.put(key, value);
            req.put("direct", true);
            req.put("deviceId", id);
            if (device.isPresent()) {
                System.out.println("Master action sent " + req);
                System.out.println("Device type " + device.get().getType());
                handleAction(id, req, device.get().getType());
            }


        }

        if (payload.containsKey("direct")) {
            sendDirectAction(deviceId, payload);
            return "No saved action found but sent directly";
        }

        var automations = automationRepository.findByTrigger_DeviceId(deviceId);
        automations.forEach(a -> checkAndExecuteSingleAutomation(a, payload, true));

        notificationService.sendNotification("Action applied", "success");
        return "Action successfully sent!";
    }

    private void sendToTopic(String topic, Map<String, Object> payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            mqttOutboundChannel.send(
                    MessageBuilder.withPayload(json)
                            .setHeader("mqtt_topic", topic)
                            .build()
            );
            System.out.println("📤 Sent to " + topic + " => " + json);
        } catch (Exception e) {
            System.err.println(e);
        }
    }

    private void notifyBasedOnResult(String result) {
        if ("success".equals(result)) {
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
        sendToTopic("automata/action/" + deviceId, map);
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
                case "bright" -> wled.setBrightness(Integer.parseInt(payload.get(key).toString())).resultNow();
                case "onOff" -> wled.powerOnOff(Boolean.parseBoolean(payload.get(key).toString())).resultNow();
                case "toggle" -> wled.toggleOnOff().resultNow();
                case "preset" -> wled.setPresets(Integer.parseInt(payload.get(key).toString())).resultNow();
                default -> "No action found for key: " + key;
            };
            CompletableFuture.runAsync(() -> {
                var data = wled.getInfo(deviceId, deviceState);
//                System.err.println(data);
                mainService.saveData(deviceId, data);
                messagingTemplate.convertAndSend("/topic/data", Map.of("deviceId", deviceId, "data", data));
//                sendToTopic("automata/data", Map.of("deviceId", deviceId, "data", data));

            });
            return "success";
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

    public void checkAndExecuteSingleAutomation(Automation automation, Map<String, Object> data, boolean executeNow) {
//        System.err.println("Checking automation: " + automation.getName());
        var payload = new HashMap<String, Object>();
        var deviceId = automation.getTrigger().getDeviceId();
        if (data != null)
            payload.putAll(data);
        else {
            payload.putAll((HashMap<String, Object>) mainService.getLastData(deviceId));
        }

        var type = automation.getTriggerDeviceType();
//        System.err.println(automation.getName() + " " + type);
        if (type != null && type.equals("System"))
            payload = (HashMap<String, Object>) mainService.getLastData(deviceId);
//        System.out.println(payload);


        long COOLDOWN_MS = 60 * 1000;

        String key = deviceId + ":" + automation.getId();
        AutomationCache automationCache = redisService.getAutomationCache(key);
        boolean isTriggeredNow = isTriggered(automation, payload);

        Date now = new Date();

        if (automationCache == null) {
            automationCache = AutomationCache.builder()
                    .id(automation.getId())
                    .automation(automation)
                    .isActive(false)
                    .triggerDeviceId(deviceId)
                    .wasTriggeredPreviously(false)
                    .triggerDeviceType(type)
                    .lastUpdate(new Date(0)) // set to epoch to allow immediate first-time execution
                    .build();
        }

        boolean cooldownElapsed = now.getTime() - automationCache.getLastUpdate().getTime() >= COOLDOWN_MS;
        boolean shouldExecute = isTriggeredNow && (!automationCache.isWasTriggeredPreviously() || executeNow);
        automationCache.setWasTriggeredPreviously(isTriggeredNow); // for next call

        if (shouldExecute) {
            automation.setIsActive(true);

            automationCache.setLastUpdate(now); // update last execution time
            System.err.println("Executing automation: " + automation.getName() + " with payload: " + payload);
            notificationService.sendNotification("Executing automation: " + automation.getName(), "low");
            executeActions(automation);
        } else {
            automation.setIsActive(false);
        }

        redisService.setAutomationCache(key, automationCache);
    }


    // new version
    public Object sendConditionToDevice(String deviceId) {
        var automations = automationRepository.findByTrigger_DeviceId(deviceId);
        var payload = new HashMap<String, Object>();
        var keyJoiner = new StringJoiner(",");

        for (Automation automation : automations) {
//            System.err.println(automation.getName());

            String key = automation.getTrigger().getKey();
            var conditions = automation.getConditions();

            if (conditions == null || conditions.isEmpty()) {
                continue; // or handle accordingly
            }

            var condition = conditions.get(0);
            String pKey = automation.getId(); // Make sure key is string
            String pVal = key;

            if (Boolean.TRUE.equals(condition.getIsExact())) {
                pVal += "=" + condition.getValue();
            } else {
                pVal += ">" + condition.getAbove() + ",<" + condition.getBelow();
            }

            payload.put(pKey, pVal);
            keyJoiner.add(pKey);
        }

        payload.put("keys", keyJoiner.toString());

//        System.err.println(payload);
        messagingTemplate.convertAndSend("/topic/action/" + deviceId, payload);
        sendToTopic("automata/action/" + deviceId, payload);
        return payload;
    }

    private boolean isTriggeredOld(Automation automation, Map<String, Object> payload) {
        if ("time".equals(automation.getTrigger().getType())) {
            return isCurrentTime(automation.getConditions().get(0).getTime());
        }

        var key = automation.getTrigger().getKeys();
        if (!payload.containsKey(key)) return false;
        var conditions = automation.getConditions();
        var truths = new ArrayList<Boolean>();
        for (var condition : conditions) {
            var value = payload.get(key).toString();
            var numericValue = Double.parseDouble(value);

            if ("state".equals(automation.getTrigger().getType()) || "periodic".equals(automation.getTrigger().getType())) {
                truths.add(checkCondition(numericValue, value, condition));
            }
        }


        return truths.stream().allMatch(Boolean::booleanValue);
    }

    private boolean isNumeric(String input) {
        return input != null && input.matches("-?\\d+(\\.\\d+)?");
    }

    private boolean isText(String input) {
        return input != null && input.matches("[a-zA-Z]+"); // only alphabets
    }

    private boolean isTriggered(Automation automation, Map<String, Object> payload) {
        if ("time".equals(automation.getTrigger().getType())) {
            return isCurrentTime(automation.getConditions().get(0).getTime());
        }


        var triggerKeys = automation.getTrigger().getKeys(); // List<TriggerKey>
        var conditions = automation.getConditions();         // List<Condition>
        if (triggerKeys == null)
            return isTriggeredOld(automation, payload);
//        System.err.println(automation.getName());
//        System.err.println(payload);
        var truths = new ArrayList<Boolean>();

        for (var key : triggerKeys) {

            if (!payload.containsKey(key)) return false;

            String value = payload.get(key).toString();
            var condition = conditions.stream()
                    .filter(c -> c.getTriggerKey().equals(key))
                    .findFirst()
                    .orElse(null);
            if (condition == null) continue;
            if (isNumeric(value)) {
                double numericValue = Double.parseDouble(value);
                if ("state".equals(automation.getTrigger().getType()) || "periodic".equals(automation.getTrigger().getType())) {
                    truths.add(checkCondition(numericValue, value, condition));
                }
            } else {
                truths.add(value.equals(condition.getValue()));
            }
        }
//        System.err.println(truths.stream().allMatch(Boolean::booleanValue));

        return truths.stream().allMatch(Boolean::booleanValue);
    }


    private Boolean checkCondition(Double numericValue, String value, Automation.Condition condition) {
        if (condition.getIsExact()) {
            return value.equals(condition.getValue());
        }

        double above = Double.parseDouble(condition.getAbove());
        double below = Double.parseDouble(condition.getBelow());
//        System.err.println(below+" "+above+" "+Double.parseDouble(condition.getValue()));
        switch (condition.getCondition()) {
            case "above" -> {
                return numericValue > Double.parseDouble(condition.getValue());
            }
            case "below" -> {
                return numericValue < Double.parseDouble(condition.getValue());
            }
            case "range" -> {
                return numericValue > above && numericValue < below;
            }
        }
        return false;
    }

    private boolean isCurrentTime(String triggerTime) {
        ZoneId istZone = ZoneId.of("Asia/Kolkata");
        LocalTime current = LocalTime.now(istZone);

        // Clean up any stray whitespace just in case
        String timeText = triggerTime == null ? "" : triggerTime.trim();

        LocalTime target = null;

        try {
            // Try parsing 24-hour format first: "HH:mm:ss"
            target = LocalTime.parse(timeText, DateTimeFormatter.ofPattern("HH:mm:ss"));
        } catch (Exception e1) {
            try {
                // Fallback to 12-hour format: "hh:mm:ss a"
                DateTimeFormatter formatter12 = new DateTimeFormatterBuilder()
                        .parseCaseInsensitive()
                        .appendPattern("hh:mm:ss a")
                        .toFormatter(Locale.ENGLISH);
                target = LocalTime.parse(timeText, formatter12);
            } catch (Exception e2) {
                System.err.println("⚠️ Unable to parse triggerTime: '" + triggerTime + "'");
                e2.printStackTrace();
                return false; // or handle gracefully
            }
        }

        long diff = Math.abs(ChronoUnit.MINUTES.between(target, current));
        return diff <= 1;
    }



    private void executeActions(Automation automation) {
        for (Automation.Action action : automation.getActions()) {
            if (Boolean.FALSE.equals(action.getIsEnabled())) continue;

            Object parsedData = parseData(action.getData());

            Map<String, Object> payload = Map.of(
                    action.getKey(), parsedData,
                    "key", action.getKey()
            );

            if ("alert".equals(action.getKey())) {
                notificationService.sendAlert("Alert: " + action.getData().toUpperCase(Locale.ROOT), action.getData());
            } else if ("app_notify".equals(action.getKey())) {
                notificationService.sendNotify("Automation", action.getData(), "low");
            } else if ("WLED".equals(mainService.getDevice(action.getDeviceId()).getType())) {
                handleWLED(action.getDeviceId(), new HashMap<>(payload));
            } else {
                messagingTemplate.convertAndSend("/topic/action/" + action.getDeviceId(), payload);
                sendToTopic("automata/action/" + action.getDeviceId(), payload);
            }
        }
    }

    /**
     * Attempts to parse a string into the appropriate data type:
     * - Boolean ("true"/"false")
     * - Integer/Double (numeric)
     * - Otherwise, returns as String
     */
    private Object parseData(String data) {
        if (data == null) return null;

        // Try boolean
        if ("true".equalsIgnoreCase(data)) return Boolean.TRUE;
        if ("false".equalsIgnoreCase(data)) return Boolean.FALSE;

        // Try number
        try {
            if (data.contains(".")) {
                return Double.parseDouble(data);
            } else {
                return Integer.parseInt(data);
            }
        } catch (NumberFormatException ignored) {
            // Not a number — fall through
        }

        // Default: string
        return data;
    }


    @EventListener
    public void onCustomEvent(LiveEvent event) {
        var payload = event.getPayload();
        var deviceId = payload.get("device_id").toString();
//        System.err.println("Redis: " + deviceId);
        redisService.setRecentDeviceData(deviceId, payload);
//        var auto = redisService.getAutomationByTriggerDevice(deviceId);
//        if (deviceId.equals("670edfe8166ab22722fbf728"))
//            System.err.println("Auto: " + auto);
//        auto.forEach(k -> checkAndExecuteSingleAutomation(k.getAutomation(), payload, false));

    }

    @Scheduled(fixedRate = 8000)
    private void triggerPeriodicAutomations() {
        automationRepository.findByIsEnabledTrue().forEach(a ->
                checkAndExecuteSingleAutomation(a, redisService.getRecentDeviceData(a.getTrigger().getDeviceId()), false));
    }

    @Scheduled(fixedRate = 1000 * 60 * 5)
    private void updateRedisStorage() {
        automationRepository.findAll().forEach(a -> {
            AutomationCache existing = redisService.getAutomationCache(a.getId());

            AutomationCache updatedCache = AutomationCache.builder()
                    .id(a.getId())
                    .automation(a)
                    .triggerDeviceType(a.getTriggerDeviceType())
                    .enabled(a.getIsEnabled())
                    .triggerDeviceId(a.getTrigger().getDeviceId())
                    .isActive(existing != null ? existing.getIsActive() : false)
                    .wasTriggeredPreviously(existing != null && existing.isWasTriggeredPreviously())
                    .lastUpdate(new Date())
                    .build();

            redisService.setAutomationCache(a.getTrigger().getDeviceId() + ":" + a.getId(), updatedCache);
            System.err.println("Redis: " + a.getTrigger().getDeviceId() + ":" + a.getId());
        });
    }


    public String saveAutomationDetail(AutomationDetail detail) {
        System.err.println(detail);
        var automationBuilder = Automation.builder()
                .isEnabled(true)
                .isActive(false);

        if (detail.getId() != null && !detail.getId().isEmpty()) automationBuilder.id(detail.getId());

        detail.getNodes().stream().filter(n -> n.getData().getTriggerData() != null).findFirst().ifPresent(triggerNode -> {
            var tData = triggerNode.getData().getTriggerData();
            automationBuilder.trigger(new Automation.Trigger(tData.getDeviceId(), tData.getType(), tData.getValue(), tData.getKey(), tData.getKeys().stream().map(t -> t.getKey()).toList(), tData.getName()));
            automationBuilder.name(tData.getName());
        });

        var actions = detail.getNodes().stream()
                .filter(n -> n.getData().getActionData() != null)
                .map(n -> {
                    var a = n.getData().getActionData();
                    return new Automation.Action(a.getKey(), a.getDeviceId(), a.getData(), a.getName(), a.getIsEnabled());
                }).toList();

        automationBuilder.actions(actions);

        var conditionList = detail.getNodes().stream()
                .map(n -> n.getData().getConditionData())
                .filter(Objects::nonNull)
                .map(c -> new Automation.Condition(
                        c.getCondition(),
                        c.getValueType(),
                        c.getAbove(),
                        c.getBelow(),
                        c.getValue(),
                        c.getTime(),
                        c.getTriggerKey(),
                        c.getIsExact()
                ))
                .collect(Collectors.toList());

        automationBuilder.conditions(conditionList);

        var automation = automationBuilder.build();
        var device = mainService.getDevice(automation.getTrigger().getDeviceId());
        automation.setTriggerDeviceType(device.getType());
        var saved = automationRepository.save(automation);
        detail.setId(saved.getId());
        automationDetailRepository.save(detail);

        notificationService.sendNotification("Automation saved successfully", "success");
        updateRedisStorage();
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
        updateRedisStorage();
        return "success";
    }

    public String ackAction(String deviceId, Map<String, Object> payload) {
        if (payload.containsKey("actionAck")) {
            messagingTemplate.convertAndSend("/topic/data", Map.of("deviceId", deviceId, "ack", payload));
//            sendToTopic("automata/data", Map.of("deviceId", deviceId, "ack", payload));
        }
        return "success";
    }

    public String rebootAllDevices() {
        var devices = deviceRepository.findAll();
        RestTemplate restTemplate = new RestTemplate();
        notificationService.sendNotification("Rebooting All Devices", "success");
        devices.forEach(device -> {
            Map<String, Object> map = Map.of("deviceId", device.getId(), "reboot", true, "key", "reboot");
            messagingTemplate.convertAndSend("/topic/action/" + device.getId(), map);
            sendToTopic("automata/action/" + device.getId(), map);
            try {
                var res = restTemplate.getForObject(device.getAccessUrl() + "/restart", String.class);
                System.err.println(res);
            } catch (Exception e) {
                notificationService.sendNotification("Reboot action failed for device: " + device.getName(), "error");
                System.err.println(e.getMessage());
            }
        });
        notificationService.sendNotification("Reboot Complete", "success");
        return "success";
    }
}
