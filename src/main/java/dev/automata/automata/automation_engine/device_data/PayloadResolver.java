package dev.automata.automata.automation_engine.device_data;

import java.util.Map;
import java.util.Optional;

public interface PayloadResolver {
    /**
     * Returns empty to mean "try the next resolver", not "condition is false."
     */
    Optional<Map<String, Object>> resolve(String deviceId, boolean isStaleCondition, String automationId);
}

