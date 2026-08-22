package dev.automata.automata.automation_engine.device_data;

import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;


@Component
@Order(1)
@RequiredArgsConstructor
public class SecondaryCacheResolver implements PayloadResolver {
    private final InMemoryCache inMemoryCache;

    @Override
    public Optional<Map<String, Object>> resolve(String deviceId, boolean isStaleCondition, String automationId) {
        Map<String, Object> cache = inMemoryCache.getValue(deviceId);
        if (cache != null) {
            return Optional.of(cache);
        }
        return Optional.empty();
    }
}
