package dev.automata.automata.service;

import dev.automata.automata.dto.AutomationCache;
import lombok.AllArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

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
        var list = redisTemplate;
    }

}
