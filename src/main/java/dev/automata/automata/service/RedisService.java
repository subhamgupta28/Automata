package dev.automata.automata.service;

import dev.automata.automata.dto.AutomationCache;
import dev.automata.automata.model.Data;
import lombok.AllArgsConstructor;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@AllArgsConstructor
public class RedisService {

    private final RedisTemplate<String, AutomationCache> redisTemplate;
//    private final RedisTemplate<String, Map<String, Object>> dataRedisTemplate;
//
//    public Map<String, Object> getData(String id) {
//        return dataRedisTemplate.opsForValue().get("data_" + id);
//    }
//
//    public void setData(String id, Map<String, Object> data) {
//        dataRedisTemplate.opsForValue().set("data_" + id, data);
//    }

    public AutomationCache getAutomationCache(String deviceId) {
        return redisTemplate.opsForValue().get(deviceId);
    }

    public void setAutomationCache(String deviceId, AutomationCache automationCache) {
        redisTemplate.opsForValue().set(deviceId, automationCache);
    }

    public void removeAutomationCache(String deviceId) {
        redisTemplate.delete(deviceId);
    }

    public List<AutomationCache> getAutomationByTriggerDevice(String deviceId) {
        Set<String> keys = redisTemplate.keys("*");
        var list = new ArrayList<AutomationCache>();
        keys.forEach(k -> list.add(getAutomationCache(k)));
        return list.stream().filter(k -> k.getAutomation().getTrigger().getDeviceId().equals(deviceId)).toList();
    }

    public void clearAutomationCache() {
        Set<String> keys = redisTemplate.keys("*"); // Get all keys
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys); // Delete all keys
        }
    }

}
