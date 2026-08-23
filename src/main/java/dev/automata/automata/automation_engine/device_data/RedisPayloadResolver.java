package dev.automata.automata.automation_engine.device_data;

import dev.automata.automata.service.RedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
@Order(2)
@RequiredArgsConstructor
public class RedisPayloadResolver implements PayloadResolver {

    private final RedisService redisService;
    private final InMemoryCache inMemoryCache;

    @Override
    public Optional<Map<String, Object>> resolve(String deviceId, boolean isStaleCondition, String automationId) {
        Map<String, Object> secondary = redisService.getRecentDeviceData(deviceId);
        if (secondary != null && !secondary.isEmpty()) {
            inMemoryCache.setValue(deviceId, secondary);
            return Optional.of(secondary);
        }
        return Optional.empty();
    }
}
