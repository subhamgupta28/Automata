package dev.automata.automata.automation_engine.device_data;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

import static dev.automata.automata.automation_engine.AutomationEvaluator.SECONDARY_CACHE;

@Component
@Order(1)
public class SecondaryCacheResolver implements PayloadResolver {
    @Override
    public Optional<Map<String, Object>> resolve(String deviceId, boolean isStaleCondition, String automationId) {
        Map<String, Map<String, Object>> cache = SECONDARY_CACHE.get();
        if (cache.containsKey(deviceId)) {
            return Optional.of(cache.get(deviceId));
        }
        return Optional.empty();
    } /* SECONDARY_CACHE lookup */
}
