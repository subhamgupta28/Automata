package dev.automata.automata.cache;

import dev.automata.automata.model.Device;
import dev.automata.automata.repository.DeviceRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache of full Device records, following the same pattern as
 * DeviceHomeCache (warm at startup, serve from memory, async-refresh on
 * miss) but caching the whole Device object rather than just homeId.
 * <p>
 * Used to remove synchronous Mongo device lookups from:
 * - ExecutionPlanCompiler.resolveDeviceType()  (per action, per compile)
 * - ActionDispatcher.dispatchWled/dispatchMedia (per dispatched action)
 * <p>
 * Devices don't change type/accessUrl/name often, so a plain in-memory map
 * refreshed on a schedule (see refresh() below, wire up with @Scheduled in
 * a config class, or call invalidate() from wherever devices are edited)
 * is sufficient — no Redis roundtrip needed for data this stable.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceMetaCache {

    private final DeviceRepository deviceRepo;
    private final ThreadPoolTaskExecutor taskExecutor;

    private final ConcurrentHashMap<String, Device> cache = new ConcurrentHashMap<>();

    public Optional<Device> getDevice(String deviceId) {
        Device cached = cache.get(deviceId);
        if (cached != null) return Optional.of(cached);

        // Miss — load synchronously ONCE (unavoidable for a never-seen
        // device), then cache it. This still beats the previous behavior
        // (a full uncached Mongo hit on EVERY call, forever).
        Optional<Device> fromDb = deviceRepo.findById(deviceId);
        fromDb.ifPresent(d -> cache.put(deviceId, d));
        return fromDb;
    }

    /**
     * Call this from wherever device edits are saved, so the cache doesn't serve stale data.
     */
    public void invalidate(String deviceId) {
        cache.remove(deviceId);
    }

    @PostConstruct
    public void warmCache() {
        try {
            List<Device> devices = deviceRepo.findAll();
            devices.forEach(d -> cache.put(d.getId(), d));
            log.info("DeviceMetaCache warmed: {} devices", devices.size());
        } catch (Exception e) {
            log.warn("DeviceMetaCache warm failed: {}", e.getMessage());
        }
    }

    /**
     * Optional periodic refresh — wire up with @Scheduled if devices get edited outside your own save paths.
     */
    public void refreshAsync() {
        CompletableFuture.runAsync(this::warmCache, taskExecutor);
    }
}
