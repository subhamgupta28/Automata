package dev.automata.automata.automation_engine.device_data;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ChainedPayloadResolver {
    private final List<PayloadResolver> resolvers; // Spring injects in @Order sequence

    public Map<String, Object> resolve(String deviceId, boolean isStaleCondition, String automationId) {
        for (var r : resolvers) {
            var result = r.resolve(deviceId, isStaleCondition, automationId);
            if (result.isPresent()) return result.get();
        }
        return null; // caller's existing null-handling (hold previous state) is unchanged
    }
}
