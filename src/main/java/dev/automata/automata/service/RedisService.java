package dev.automata.automata.service;

import dev.automata.automata.dto.AutomationCache;
import lombok.AllArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@AllArgsConstructor
public class RedisService {

    private final RedisTemplate<String, AutomationCache> redisTemplate;

    public AutomationCache getAutomationCache(String deviceId) {
        return redisTemplate.opsForValue().get(deviceId);
    }

    public void setAutomationCache(String deviceId, AutomationCache automationCache) {
        redisTemplate.opsForValue().set(deviceId, automationCache);
    }

    public void removeAutomationCache(String deviceId) {
        redisTemplate.delete(deviceId);
    }

    public void clearAutomationCache() {
        Set<String> keys = redisTemplate.keys("*"); // Get all keys
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys); // Delete all keys
        }
    }

}
