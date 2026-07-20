package dev.automata.automata.automation_extras;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.automata.automata.automation_engine.AutomationLogStream;
import dev.automata.automata.model.AutomationLog;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Redis-backed action delivery tracker.
 * <p>
 * Previously an in-memory Map — broken in distributed envs because the node
 * that registers a correlation ID may not be the node that receives the MQTT ack.
 * <p>
 * Now stores delivery records under DELIVERY:<correlationId> with a 60s TTL.
 * Any node that handles ackAction() can look up and confirm the record.
 * <p>
 * Key: DELIVERY:<correlationId>
 * TTL: 60 seconds (device must ack within 1 minute or the record expires)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActionDeliveryTracker {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final AutomationLogStream logStream;

    private static final String PREFIX = "DELIVERY:";
    private static final long TTL_SEC = 60L;
    private static final long WLED_TIMEOUT_MS = 15_000L; // WLED responds fast over MQTT

    // ─────────────────────────────────────────────────────────────────────
    // REGISTER
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Register a pending delivery. Called by ActionDispatcher after sending
     * the action payload to the device.
     */
    public void register(String correlationId,
                         String automationId,
                         String automationName,
                         String deviceId,
                         String deviceName,
                         Map<String, Object> payload, String traceId) {
        try {
            DeliveryRecord record = DeliveryRecord.builder()
                    .correlationId(correlationId)
                    .automationId(automationId)
                    .automationName(automationName)
                    .deviceId(deviceId)
                    .deviceName(deviceName)
                    .payload(payload)
                    .registeredAt(new Date())
                    .traceId(traceId)
                    .build();

            String json = objectMapper.writeValueAsString(record);
            redisTemplate.opsForValue().set(PREFIX + correlationId, json, TTL_SEC, TimeUnit.SECONDS);

            log.debug("📬 Delivery registered: cid={} automation='{}' device='{}'",
                    correlationId, automationName, deviceName);

        } catch (Exception e) {
            log.warn("Failed to register delivery for cid='{}': {}", correlationId, e.getMessage());
        }
    }

    /**
     * Register a WLED dispatch for delivery tracking.
     * Called from ActionDispatcher.dispatchWled() instead of marking NOT_APPLICABLE.
     *
     * @param deviceId       the WLED device ID (used to match the incoming /v message)
     * @param automationId   for logging
     * @param automationName for logging
     * @param deviceName     for logging
     * @param traceId        from the originating execute() call
     */
    public void registerWled(String deviceId,
                             String automationId,
                             String automationName,
                             String deviceName,
                             Map<String, Object> payload, String traceId) {

        try {
            DeliveryRecord record = DeliveryRecord.builder()
                    .automationId(automationId)
                    .automationName(automationName)
                    .deviceId(deviceId)
                    .deviceName(deviceName)
                    .payload(payload)
                    .registeredAt(new Date())
                    .traceId(traceId)
                    .build();

            String json = objectMapper.writeValueAsString(record);
            redisTemplate.opsForValue().set(PREFIX + deviceId, json, TTL_SEC, TimeUnit.SECONDS);
            log.info("📡 WLED delivery pending: device='{}' traceId='{}'", deviceId, traceId);
        } catch (Exception e) {
            log.warn("Failed to register delivery for cid='{}': {}", deviceName, e.getMessage());
        }

    }

    // ─────────────────────────────────────────────────────────────────────
    // CONFIRM
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Confirm delivery. Called from AutomationService.ackAction() when a device
     * sends back an ack containing the correlationId (_cid field).
     * <p>
     * Uses GETDEL to atomically read and delete — prevents double-confirmation.
     * Any node in the cluster can handle this ack.
     */
    public void confirm(String correlationId) {
        try {
            // GETDEL — Redis 6.2+; falls back to GET+DEL if unavailable
            String raw = redisTemplate.opsForValue().getAndDelete(PREFIX + correlationId);

            if (raw == null) {
                log.debug("📭 Ack received for unknown/expired cid='{}'", correlationId);
                return;
            }

            DeliveryRecord record = objectMapper.readValue(raw, DeliveryRecord.class);
            long latencyMs = new Date().getTime() - record.getRegisteredAt().getTime();
            logStream.updateDeliveryStatus(
                    record.getTraceId(),
                    AutomationLog.DeliveryStatus.DELIVERED,
                    new Date());
            log.info("✅ Action delivered: automation='{}' device='{}' latency={}ms cid='{}'",
                    record.getAutomationName(), record.getDeviceName(), latencyMs, correlationId);

        } catch (Exception e) {
            log.warn("Failed to confirm delivery for cid='{}': {}", correlationId, e.getMessage());
        }
    }

    /**
     * Called from the WLED MQTT handler when a /v state message arrives.
     * This is the WLED equivalent of a device ACK — it confirms the command
     * was received and applied (WLED only publishes /v after processing a command).
     *
     * @param deviceId the device ID resolved from the MQTT topic
     */
    public void confirmWled(String deviceId, String deviceName) {
        try {
            String raw = redisTemplate.opsForValue().getAndDelete(PREFIX + deviceId);
            if (raw == null) {
                log.debug("📭 Ack received for unknown/expired cid='{}'", deviceId);
                return;
            }

            DeliveryRecord record = objectMapper.readValue(raw, DeliveryRecord.class);
            long latencyMs = new Date().getTime() - record.getRegisteredAt().getTime();
            log.info("✅ WLED delivered: automation='{}' device='{}' latency={}ms traceId={}",
                    record.getAutomationName(), deviceName,
                    latencyMs, record.getTraceId());

            logStream.updateDeliveryStatus(
                    record.getTraceId(),
                    AutomationLog.DeliveryStatus.DELIVERED,
                    new Date());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }


    }

    @Scheduled(fixedRate = 30_000)
    public void evictExpired() {
        long now = System.currentTimeMillis();
        long timeoutMs = 60_000; // match ACTION_TIMEOUT_SECONDS in ActionDispatcher

//        var pending = redisTemplate.opsForStream()
//        pending.entrySet().removeIf(entry -> {
//            PendingAction action = entry.getValue();
//            if (now - action.registeredAt() > timeoutMs) {
//                log.warn("⚠️ Delivery unconfirmed after {}ms: automation='{}' device='{}' traceId={}",
//                        timeoutMs, action.automationName(), action.deviceId(), action.traceId());
//
//                // Mark log entry as DELIVERY_FAILED
//                logStream.updateDeliveryStatus(
//                        action.traceId(),
//                        AutomationLog.DeliveryStatus.DELIVERY_FAILED,
//                        new Date());
//                return true;
//            }
//            return false;
//        });
    }
    // ─────────────────────────────────────────────────────────────────────
    // RECORD TYPE
    // ─────────────────────────────────────────────────────────────────────

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeliveryRecord {
        private String correlationId;
        private String automationId;
        private String automationName;
        private String deviceId;
        private String deviceName;
        private Map<String, Object> payload;
        private Date registeredAt;
        String traceId;
    }
}
