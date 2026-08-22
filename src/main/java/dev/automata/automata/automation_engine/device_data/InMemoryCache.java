package dev.automata.automata.automation_engine.device_data;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Per-evaluation in-memory cache for secondary device data.
 * Keyed by deviceId → payload map. Created fresh for each evaluate() call.
 */
@Component
public class InMemoryCache {

    private static final ThreadLocal<Map<String, Map<String, Object>>> SECONDARY_CACHE =
            ThreadLocal.withInitial(HashMap::new);

    public Map<String, Object> getValue(String deviceId) {
        return SECONDARY_CACHE.get().get(deviceId);
    }

    public void setValue(String key, Map<String, Object> payload) {
        SECONDARY_CACHE.get().put(key, payload);
    }

    public void clear() {
        SECONDARY_CACHE.get().clear();
    }
}
