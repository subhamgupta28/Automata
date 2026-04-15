package dev.automata.automata.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.automata.automata.dto.AutomationCache;
import dev.automata.automata.dto.AutomationState;
import dev.automata.automata.model.Automation;
import dev.automata.automata.repository.AutomationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationUtils {

    private final AutomationRepository automationRepository;
    private final RedisService redisService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;
    // In AutomationService constants
    private static final String SNOOZE_KEY = "SNOOZE:";   // SNOOZE:{automationId}
    private static final String DISABLE_KEY = "TIMED_DISABLE:"; // TIMED_DISABLE:{automationId}
    // ─── SNOOZE ───────────────────────────────────────────────────────────────

    public String snoozeAutomation(String automationId, int durationMinutes) {
        automationRepository.findById(automationId).ifPresent(a -> {
            String key = SNOOZE_KEY + automationId;
            long ttl = durationMinutes * 60L;

            redisService.setWithExpiry(key, String.valueOf(System.currentTimeMillis() + ttl * 1000), ttl);

            // Record snooze metadata so UI can show "snoozed until X"
            String metaKey = SNOOZE_KEY + "META:" + automationId;
            Map<String, Object> meta = Map.of(
                    "until", System.currentTimeMillis() + ttl * 1000,
                    "minutes", durationMinutes,
                    "name", a.getName()
            );
            try {
                redisService.setWithExpiry(metaKey, objectMapper.writeValueAsString(meta), ttl);
            } catch (Exception e) {
                log.warn("Failed to write snooze meta for {}", automationId);
            }

            notificationService.sendNotification(
                    "⏸️ " + a.getName() + " snoozed for " + durationMinutes + " min", "info");

            log.info("⏸️ Snoozed automation '{}' for {} minutes", a.getName(), durationMinutes);

            // Broadcast state to UI via websocket
            broadcastSnoozeState(automationId, "SNOOZED", durationMinutes);
        });
        return "snoozed";
    }

// ─── TIMED DISABLE ────────────────────────────────────────────────────────

    public String timedDisableAutomation(String automationId, int durationMinutes) {
        automationRepository.findById(automationId).ifPresent(a -> {

            if (durationMinutes <= 0) {
                // Permanent disable — use existing flag
                a.setIsEnabled(false);
                automationRepository.save(a);
                refreshCacheForAutomation(a);
                notificationService.sendNotification(
                        "🚫 " + a.getName() + " disabled", "info");
                broadcastSnoozeState(automationId, "DISABLED", 0);
                return;
            }

            // Temporary — keep isEnabled=true in DB, block in Redis
            String key = DISABLE_KEY + automationId;
            long ttl = durationMinutes * 60L;
            redisService.setWithExpiry(key, "disabled", ttl);

            String metaKey = DISABLE_KEY + "META:" + automationId;
            Map<String, Object> meta = Map.of(
                    "until", System.currentTimeMillis() + ttl * 1000,
                    "minutes", durationMinutes,
                    "name", a.getName()
            );
            try {
                redisService.setWithExpiry(metaKey, objectMapper.writeValueAsString(meta), ttl);
            } catch (Exception e) {
                log.warn("Failed to write disable meta for {}", automationId);
            }

            notificationService.sendNotification(
                    "🚫 " + a.getName() + " disabled for " + durationMinutes + " min", "info");

            log.info("🚫 Timed-disabled automation '{}' for {} minutes", a.getName(), durationMinutes);
            broadcastSnoozeState(automationId, "TIMED_DISABLED", durationMinutes);
        });
        return "disabled";
    }

// ─── RESUME ───────────────────────────────────────────────────────────────

    public String resumeAutomation(String automationId) {
        automationRepository.findById(automationId).ifPresent(a -> {
            redisService.delete(SNOOZE_KEY + automationId);
            redisService.delete(SNOOZE_KEY + "META:" + automationId);
            redisService.delete(DISABLE_KEY + automationId);
            redisService.delete(DISABLE_KEY + "META:" + automationId);

            // Also re-enable in DB in case it was permanently disabled
            if (!a.getIsEnabled()) {
                a.setIsEnabled(true);
                automationRepository.save(a);
                refreshCacheForAutomation(a);
            }

            notificationService.sendNotification(
                    "▶️ " + a.getName() + " resumed", "success");

            log.info("▶️ Resumed automation '{}'", a.getName());
            broadcastSnoozeState(automationId, "ACTIVE", 0);
        });
        return "resumed";
    }

// ─── STATUS ───────────────────────────────────────────────────────────────

    public Map<String, Object> getSnoozeStatus(String automationId) {
        boolean snoozed = redisService.exists(SNOOZE_KEY + automationId);
        boolean timedDisabled = redisService.exists(DISABLE_KEY + automationId);

        Map<String, Object> status = new HashMap<>();
        status.put("automationId", automationId);
        status.put("snoozed", snoozed);
        status.put("timedDisabled", timedDisabled);

        // Remaining TTL
        if (snoozed) {
            long ttl = redisService.getTTL(SNOOZE_KEY + automationId);
            status.put("snoozeRemainingSeconds", ttl);
            status.put("snoozeRemainingMinutes", ttl / 60);
            readMeta(SNOOZE_KEY + "META:" + automationId).ifPresent(m -> status.put("snoozeMeta", m));
        }
        if (timedDisabled) {
            long ttl = redisService.getTTL(DISABLE_KEY + automationId);
            status.put("disableRemainingSeconds", ttl);
            status.put("disableRemainingMinutes", ttl / 60);
            readMeta(DISABLE_KEY + "META:" + automationId).ifPresent(m -> status.put("disableMeta", m));
        }

        return status;
    }

    private Optional<Map> readMeta(String key) {
        try {
            Object raw = redisService.get(key);
            if (raw == null) return Optional.empty();
            return Optional.of(objectMapper.readValue(raw.toString(), Map.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private void broadcastSnoozeState(String automationId, String state, int remainingMinutes) {
        messagingTemplate.convertAndSend("/topic/automation.snooze",
                Map.of(
                        "automationId", automationId,
                        "state", state,
                        "remainingMinutes", remainingMinutes,
                        "timestamp", System.currentTimeMillis()
                ));
    }

    private void refreshCacheForAutomation(Automation automation) {
        String cacheKey = automation.getTrigger().getDeviceId() + ":" + automation.getId();
        try {
            AutomationCache existing = redisService.getAutomationCache(cacheKey);
            redisService.setAutomationCache(cacheKey, AutomationCache.builder()
                    .id(automation.getId()).automation(automation)
                    .triggerDeviceType(automation.getTriggerDeviceType())
                    .enabled(automation.getIsEnabled())
                    .state(existing != null ? existing.getState() : AutomationState.IDLE)
                    .triggerDeviceId(automation.getTrigger().getDeviceId())
                    .isActive(existing != null && Boolean.TRUE.equals(existing.getIsActive()))
                    .triggeredPreviously(existing != null && existing.isTriggeredPreviously())
                    .previousExecutionTime(existing != null ? existing.getPreviousExecutionTime() : null)
                    .lastUpdate(new Date())
                    .build());
            log.info("🔄 Cache refreshed: {}", automation.getName());
        } catch (Exception e) {
            log.warn("⚠️ Cache refresh failed for '{}': {}", automation.getName(), e.getMessage());
        }
    }


}
