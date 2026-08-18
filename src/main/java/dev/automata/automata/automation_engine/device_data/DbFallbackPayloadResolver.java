package dev.automata.automata.automation_engine.device_data;

import dev.automata.automata.automation_engine.StaleDeviceLookupCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
@Order(3)
@RequiredArgsConstructor
@Slf4j
public class DbFallbackPayloadResolver implements PayloadResolver {

    private final StaleDeviceLookupCache staleDeviceLookupCache;

    @Override
    public Optional<Map<String, Object>> resolve(String deviceId, boolean isStaleCondition, String automationId) {
        log.warn("⚠️ [{}] Secondary device '{}' has no Redis data — fetching from DB for stale check",
                automationId, deviceId);
        long dbStart = System.currentTimeMillis();
        var data = staleDeviceLookupCache.getLastFullData(deviceId);
        if (data == null) {
            // Timed out, failed, or genuinely never-seen — same semantics as
            // "no data resolvable" that the caller already handles.
            return Optional.of(Map.of());
        }
        long dbMs = System.currentTimeMillis() - dbStart;
        if (dbMs > 200)
            log.warn("⚠️ [{}] DB fallback for '{}' took {}ms", automationId, deviceId, dbMs);

        Map<String, Object> result = new HashMap<>();
        if (data.getData() != null) result.putAll(data.getData());
        if (data.getUpdateDate() != null)
            result.put("last_seen", data.getUpdateDate().getEpochSecond() * 1000L);

        return Optional.of(result);
    }
}
