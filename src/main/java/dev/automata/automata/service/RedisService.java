package dev.automata.automata.service;

import dev.automata.automata.dto.AutomationCache;
import dev.automata.automata.model.Data;
import lombok.AllArgsConstructor;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@AllArgsConstructor
public class RedisService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final Map<String, Map<String, Map<String, Object>>> deviceStates = new HashMap<>();
    private final Set<String> activeAutomations = new HashSet<>();
    //    private final RedisTemplate<String, Map<String, Object>> dataRedisTemplate;
//
    public Map<String, Object> getRecentDeviceData(String id) {
        return (Map<String, Object>) redisTemplate.opsForValue().get("data_" + id);
    }

    public void setRecentDeviceData(String id, Map<String, Object> data) {
        redisTemplate.opsForValue().set("data_" + id, data);
    }

    // deviceId -> automationId -> state
    public void saveAutomationState(String deviceId, String automationId,
                                    Map<String, Object> state, Integer priority) {

        deviceStates
                .computeIfAbsent(deviceId, k -> new HashMap<>())
                .put(automationId, state);

        activeAutomations.add(automationId);
    }

    public void removeAutomationState(String deviceId, String automationId) {

        if (deviceStates.containsKey(deviceId)) {
            deviceStates.get(deviceId).remove(automationId);
        }

        activeAutomations.remove(automationId);
    }

    public Map<String, Map<String, Object>> getDeviceStates(String deviceId) {
        return deviceStates.getOrDefault(deviceId, new HashMap<>());
    }

    public boolean isAutomationActive(String automationId) {
        return activeAutomations.contains(automationId);
    }

    public AutomationCache getAutomationCache(String deviceId) {
        return (AutomationCache) redisTemplate.opsForValue().get(deviceId);
    }

    public void setAutomationCache(String deviceId, AutomationCache automationCache) {
        redisTemplate.opsForValue().set(deviceId, automationCache);
    }

    public void removeAutomationCache(String deviceId) {
        redisTemplate.delete(deviceId);
    }

    public List<AutomationCache> getAutomationByTriggerDevice(String deviceId) {
        Set<String> keys = redisTemplate.keys(deviceId + ":*");
        if (keys.isEmpty()) {
            return Collections.emptyList();
        }

        return keys.stream()
                .map(this::getAutomationCache)
                .filter(AutomationCache::isEnabled)
                .filter(ac -> {
                    var automation = ac.getAutomation();
                    return automation != null &&
                            automation.getTrigger() != null &&
                            deviceId.equals(automation.getTrigger().getDeviceId());
                })
                .toList();
    }

    public void clearAutomationCache() {
        Set<String> keys = redisTemplate.keys("*"); // Get all keys
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys); // Delete all keys
        }
    }
    public boolean exists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public void setWithExpiry(String key, String value, long ttlSeconds) {
        redisTemplate.opsForValue().set(key, value, ttlSeconds, TimeUnit.SECONDS);
    }
}
