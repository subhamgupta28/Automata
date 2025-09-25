package dev.automata.automata.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.integration.core.GenericTransformer;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class SafeJsonTransformer implements GenericTransformer<Message<?>, Object> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Object transform(Message<?> message) {
        Object payload = message.getPayload();

        if (payload instanceof String strPayload) {
            String trimmed = strPayload.trim();
            try {
                if (trimmed.startsWith("{")) {
                    // Parse only if it looks like a JSON object
                    return objectMapper.readValue(trimmed, Map.class);
                } else {
                    // Not a JSON object — log and wrap
                    System.err.println("⚠️ Non-JSON payload received: " + strPayload);
                    return Map.of("raw", strPayload);
                }
            } catch (Exception e) {
                System.err.println("❌ Failed to parse payload: " + strPayload);
                e.printStackTrace();
                return Map.of("error", "Invalid JSON", "raw", strPayload);
            }
        }

        // If it's not a string (e.g. already a byte[]), return as-is or wrap
        return Map.of("raw", payload);
    }
}
