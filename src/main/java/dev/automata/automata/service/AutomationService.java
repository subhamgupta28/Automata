package dev.automata.automata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.automata.automata.dto.AutomationCache;
import dev.automata.automata.dto.LiveEvent;
import dev.automata.automata.model.Automation;
import dev.automata.automata.model.AutomationDetail;
import dev.automata.automata.model.DeviceActionState;
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


    public List<Automation> findAll() {
        return automationRepository.findAll();
    }

    public Automation create(Automation action) {
        return automationRepository.save(action);
    }

    public String handleAction(String deviceId, Map<String, Object> payload, String deviceType, String user) {
        System.err.println("Received action");
        System.err.println("Device Type: " + deviceType);
        System.err.println("Payload: " + payload);
        System.err.println("User: " + user);

        if ("WLED".equals(deviceType)) {

            var result = handleWLED(deviceId, payload, user);
            notifyBasedOnResult(result);

            return "success";
        }


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
            automationRepository.findById(id).ifPresent((automation) -> {
                executeActions(automation, user, new HashMap<>());
            });
            return "success";
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
                handleAction(id, req, device.get().getType(), user);
            }


        }

        if (payload.containsKey("direct")) {
            sendDirectAction(deviceId, payload);
            return "No saved action found but sent directly";
        }

        var automations = automationRepository.findByTrigger_DeviceId(deviceId);
        automations.forEach(a -> checkAndExecuteSingleAutomation(a, payload, true, user));

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
//            System.out.println("📤 Sent to " + topic + " => " + json);
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
            var res = new RestTemplate().getForObject( device.getAccessUrl() + "/restart", String.class);
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

    private String handleWLED(String deviceId, Map<String, Object> payload, String user) {
        var device = deviceRepository.findById(deviceId).orElse(null);
//        var deviceState = deviceActionStateRepository.findById(deviceId).orElse(null);

        if (device == null) return "Not found";

        try {
            var wled = new Wled(mqttOutboundChannel, device);
            return wled.handleAction(payload);
        } catch (Exception e) {
            System.err.println(e);
            return "Error";
        }
    }


    public void checkAndExecuteSingleAutomation(Automation automation, Map<String, Object> data, boolean executeNow, String user) {
        var payload = new HashMap<String, Object>();
        var deviceId = automation.getTrigger().getDeviceId();

        if (data != null) payload.putAll(data);
        else payload.putAll(mainService.getLastData(deviceId));

        var type = automation.getTriggerDeviceType();
        String cacheKey = deviceId + ":" + automation.getId();
        AutomationCache automationCache = redisService.getAutomationCache(cacheKey);

        if (automationCache == null) {
            automationCache = AutomationCache.builder()
                    .id(automation.getId())
                    .wasTriggeredPreviously(false)
                    .lastUpdate(new Date(0))
                    .build();
        }
        // Pass the previous state to the trigger check
        boolean isTriggeredNow = isTriggered(automation, payload, automationCache.isWasTriggeredPreviously());
        Date now = new Date();
        String triggerType = automation.getTrigger().getType(); // "time", "state", "periodic"
        // SCENARIO 1: Condition just became TRUE (Trigger)
        if (isTriggeredNow && !automationCache.isWasTriggeredPreviously()) {
            System.out.println("🚀 Automation Triggered: " + automation.getName());
            notificationService.sendNotification("Executing automation: " + automation.getName(), "low");
            // 1. Capture and Save "Before" State for all devices in this automation
            // Only snapshot if it's NOT a time-based trigger
            if (!"time".equals(triggerType)) {
                saveStateSnapshots(automation);
            }

            // 2. Execute the "Warning" actions
            executeActions(automation, user, payload);

            automationCache.setWasTriggeredPreviously(true);
            automationCache.setLastUpdate(now);
            redisService.setAutomationCache(cacheKey, automationCache);
        }

        // SCENARIO 2: Condition just became FALSE (Restoration)
        else if (!isTriggeredNow && automationCache.isWasTriggeredPreviously()) {
            System.out.println("🔄 Automation Cleared: Reverting " + automation.getName());

            // Only restore if it's a state-based trigger (like AQI or Temp)
            // We EXCLUDE "time" because we want those changes to stay
            if ("state".equals(triggerType) || "periodic".equals(triggerType)) {
                System.out.println("🔄 Restoring state for sensor-based automation");
                restoreStateSnapshots(automation, user);
            }

            automationCache.setWasTriggeredPreviously(false);
            automationCache.setLastUpdate(now);
            redisService.setAutomationCache(cacheKey, automationCache);
        }
    }

    /**
     * Saves the current state of all target devices before they get changed.
     */
    private void saveStateSnapshots(Automation automation) {
        for (Automation.Action action : automation.getActions()) {
            String targetDeviceId = action.getDeviceId();
            // Get last known good state from Redis/MainService
            Map<String, Object> currentState = (Map<String, Object>) mainService.getLastData(targetDeviceId);

            if (currentState != null && !currentState.isEmpty()) {
                String snapshotKey = "SNAPSHOT:" + automation.getId() + ":" + targetDeviceId;
                redisService.setRecentDeviceData(snapshotKey, currentState);
                System.out.println("📸 Snapshot saved for device: " + targetDeviceId + currentState);
            }
        }
    }

    /**
     * Reverts devices to the state they were in before the automation fired.
     */
    private void restoreStateSnapshots(Automation automation, String user) {
        for (Automation.Action action : automation.getActions()) {
            if (!action.getRevert())
                continue;

            String targetDeviceId = action.getDeviceId();
            String snapshotKey = "SNAPSHOT:" + automation.getId() + ":" + targetDeviceId;
            Map<String, Object> previousState = (Map<String, Object>) redisService.getRecentDeviceData(snapshotKey);

            if (previousState != null) {
                System.out.println("⏪ Restoring device: " + targetDeviceId + previousState);

                // Logic to send the state back to the device
                // We wrap the previous state in a format the device understands
                var device = deviceRepository.findById(targetDeviceId).orElse(null);
                if (device == null) continue;

                if ("WLED".equals(device.getType())) {
                    // Specific logic for WLED if you want to use the API
                    restoreWledState(targetDeviceId, previousState, user);
                } else {
                    // Generic MQTT/WS restore for other devices
                    messagingTemplate.convertAndSend("/topic/action/" + targetDeviceId, previousState);
                    sendToTopic("automata/action/" + targetDeviceId, previousState);
                }
            }
        }
    }

    private void restoreWledState(String deviceId, Map<String, Object> state, String user) {
        // Map the saved WLED snapshot back to your handleWLED method keys
        // If the snapshot has 'bright', we send a 'bright' action, etc.
        handleWLED(deviceId, state, user);
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

    private boolean isTriggeredOld(Automation automation, Map<String, Object> payload, boolean wasActive) {
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
                truths.add(checkCondition(numericValue, condition, wasActive));
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

    private boolean isTriggered(Automation automation, Map<String, Object> payload, boolean wasActive) {
        if ("time".equals(automation.getTrigger().getType())) {
            return isCurrentTime(automation.getConditions().get(0).getTime());
        }


        var triggerKeys = automation.getTrigger().getKeys(); // List<TriggerKey>
        var conditions = automation.getConditions();         // List<Condition>
        if (triggerKeys == null)
            return isTriggeredOld(automation, payload, wasActive);
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
                // Pass wasActive down to the individual condition check
                truths.add(checkCondition(numericValue, condition, wasActive));
            } else {
                truths.add(value.equals(condition.getValue()));
            }
        }
//        System.err.println(truths.stream().allMatch(Boolean::booleanValue));

        return truths.stream().allMatch(Boolean::booleanValue);
    }


    private Boolean checkCondition(Double numericValue, Automation.Condition condition, boolean wasActive) {
        if (condition.getIsExact()) {
            return condition.getValue().equals(numericValue.toString());
        }

        double threshold = Double.parseDouble(condition.getValue());
        // Define a buffer (e.g., 10% of the threshold or a fixed value like 15)
        double buffer = 5.0;

        switch (condition.getCondition()) {
            case "above" -> {
                // If already active, stay active until it drops below (threshold - buffer)
                if (wasActive) {
                    return numericValue > (threshold - buffer);
                }
                // If not active, trigger only if it goes above the threshold
                return numericValue > threshold;
            }
            case "below" -> {
                // If already active, stay active until it rises above (threshold + buffer)
                if (wasActive) {
                    return numericValue < (threshold + buffer);
                }
                return numericValue < threshold;
            }
            case "range" -> {
                double above = Double.parseDouble(condition.getAbove());
                double below = Double.parseDouble(condition.getBelow());
                // For range, we can expand the boundaries slightly when active
                if (wasActive) {
                    return numericValue > (above - buffer) && numericValue < (below + buffer);
                }
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


    private void executeActions(Automation automation, String user, Map<String, Object> value) {
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
                notificationService.sendNotify("Automation", action.getData() + "and live data are " + value, "low");
            } else if ("WLED".equals(mainService.getDevice(action.getDeviceId()).getType())) {
                handleWLED(action.getDeviceId(), new HashMap<>(payload), user);
            } else {
                deviceActionStateRepository.save(DeviceActionState.builder()
                        .user(user)
                        .deviceId(action.getDeviceId())
                        .timestamp(Date.from(ZonedDateTime.now(ZoneId.systemDefault()).toInstant()))
                        .payload(payload)
                        .deviceType("sensor")
                        .build());
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
//        auto.forEach(k -> checkAndExecuteSingleAutomation(k.getAutomation(), payload, false, "system"));

    }

    @Scheduled(fixedRate = 8000)
    private void triggerPeriodicAutomations() {
        automationRepository.findByIsEnabledTrue().forEach(a ->
                checkAndExecuteSingleAutomation(a, redisService.getRecentDeviceData(a.getTrigger().getDeviceId()), false, "system"));
    }

    @Scheduled(fixedRate = 1000 * 60 * 5)
    private void updateRedisStorage() {
        automationRepository.findAll().forEach(a -> {
            var id = a.getTrigger().getDeviceId() + ":" + a.getId();
            AutomationCache existing = redisService.getAutomationCache(id);

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

            redisService.setAutomationCache(id, updatedCache);
//            System.err.println("Redis: " + a.getTrigger().getDeviceId() + ":" + a.getId());
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
                    return new Automation.Action(
                            a.getKey(),
                            a.getDeviceId(),
                            a.getData(),
                            a.getName(),
                            a.getIsEnabled(),
                            a.getRevert()
                    );
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
