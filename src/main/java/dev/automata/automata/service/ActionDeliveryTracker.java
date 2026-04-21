package dev.automata.automata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.automata.automata.dto.PendingDelivery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ActionDeliveryTracker {

    private final RedisService redisService;
    private final NotificationService notificationService;
    private final ObjectMapper objectMapper;
    private final SimpMessagingTemplate messagingTemplate;
    private final MessageChannel mqttOutboundChannel;

    private static final String PREFIX = "PENDING_ACTION:";
    private static final int TTL_SECONDS = 30;
    private static final int MAX_ATTEMPTS = 3;

    /**
     * Called by executeSingleAction after publishing — registers the delivery.
     */
    public void register(String correlationId, String automationId, String automationName,
                         String deviceId, String deviceName, Map<String, Object> payload) {
        PendingDelivery delivery = PendingDelivery.builder()
                .correlationId(correlationId)
                .automationId(automationId)
                .automationName(automationName)
                .deviceId(deviceId)
                .deviceName(deviceName)
                .payload(payload)
                .attempts(1)
                .maxAttempts(MAX_ATTEMPTS)
                .firstSentAt(System.currentTimeMillis())
                .lastSentAt(System.currentTimeMillis())
                .build();
        persist(delivery);
    }

    /**
     * Called when the device sends back an actionAck with the correlationId.
     */
    public void confirm(String correlationId) {
        String key = PREFIX + correlationId;
        if (redisService.exists(key)) {
            redisService.delete(key);
            log.info("Action delivery confirmed: {}", correlationId);
        }
    }

    /**
     * Scans for unacknowledged deliveries older than TTL and retries or fails them.
     */
    @Scheduled(fixedDelay = 10_000)
    public void retryPending() {
        List<String> keys = redisService.scan(PREFIX + "*");
        for (String key : keys) {
            try {
                PendingDelivery d = load(key);
                if (d == null) continue;

                long age = System.currentTimeMillis() - d.getLastSentAt();
                if (age < TTL_SECONDS * 1000L) continue;  // not yet expired

                if (d.getAttempts() >= d.getMaxAttempts()) {
                    // Give up
                    redisService.delete(key);
                    log.warn("Action delivery FAILED after {} attempts — automation='{}' device='{}'",
                            d.getAttempts(), d.getAutomationName(), d.getDeviceName());
                    notificationService.sendNotification(
                            d.getAutomationName() + " — " + d.getDeviceName() + " unreachable", "error");
                    continue;
                }

                // Retry
                d.setAttempts(d.getAttempts() + 1);
                d.setLastSentAt(System.currentTimeMillis());
                persist(d);

                log.info("Retrying action delivery (attempt {}/{}) — device='{}'",
                        d.getAttempts(), d.getMaxAttempts(), d.getDeviceName());
                republish(d);

            } catch (Exception e) {
                log.error("Error processing pending delivery {}: {}", key, e.getMessage());
            }
        }
    }

    private void republish(PendingDelivery d) {
        messagingTemplate.convertAndSend("action/" + d.getDeviceId(), d.getPayload());
        try {
            String json = objectMapper.writeValueAsString(d.getPayload());
            mqttOutboundChannel.send(
                    MessageBuilder.withPayload(json)
                            .setHeader("mqtt_topic", "action/" + d.getDeviceId())
                            .build());
        } catch (Exception e) {
            log.error("Retry publish failed for {}: {}", d.getCorrelationId(), e.getMessage());
        }
    }

    private void persist(PendingDelivery d) {
        try {
            redisService.setWithExpiry(
                    PREFIX + d.getCorrelationId(),
                    objectMapper.writeValueAsString(d),
                    TTL_SECONDS * (long) d.getMaxAttempts());  // TTL covers all retries
        } catch (Exception e) {
            log.error("Failed to persist pending delivery: {}", e.getMessage());
        }
    }

    private PendingDelivery load(String key) {
        try {
            Object val = redisService.get(key);
            if (val == null) return null;
            return objectMapper.readValue(val.toString(), PendingDelivery.class);
        } catch (Exception e) {
            log.warn("Failed to deserialize pending delivery {}: {}", key, e.getMessage());
            return null;
        }
    }
}
