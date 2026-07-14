package dev.automata.automata.listener;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.integration.core.GenericTransformer;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
public class SafeJsonTransformer implements GenericTransformer<Message<?>, Object> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Message<?> transform(Message<?> message) {
        Object payload = message.getPayload();
        Map<String, Object> parsed;

        if (payload instanceof byte[] bytes) {
            // Spring Integration 7 MQTT adapter delivers byte[] by default now
            payload = new String(bytes, StandardCharsets.UTF_8);
        }

        if (payload instanceof String strPayload) {
            String trimmed = strPayload.trim();
            try {
                if (trimmed.startsWith("{")) {
                    parsed = objectMapper.readValue(trimmed, Map.class);
                } else {
                    System.err.println("⚠️ Non-JSON payload: " + strPayload);
                    parsed = Map.of("raw", strPayload);
                }
            } catch (Exception e) {
                System.err.println("❌ Failed to parse: " + strPayload);
                parsed = Map.of("error", "Invalid JSON", "raw", strPayload);
            }
        } else {
            parsed = Map.of("raw", payload.toString());
        }

        // ✅ Return a full Message, preserving ALL original headers (including mqtt_receivedTopic)
        return MessageBuilder.withPayload(parsed)
                .copyHeaders(message.getHeaders())
                .build();
    }
}
