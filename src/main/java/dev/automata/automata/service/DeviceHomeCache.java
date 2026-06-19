package dev.automata.automata.service;

import dev.automata.automata.model.Device;
import dev.automata.automata.repository.DeviceRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceHomeCache {

    private final RedisTemplate<String, String> stringRedisTemplate;
    private final DeviceRepository deviceRepo;
    // Reuse your existing taskExecutor — it's already sized for I/O work
    private final ThreadPoolTaskExecutor taskExecutor;

    private static final String PREFIX = "device:home:";
    private static final Duration TTL = Duration.ofHours(12);

    // In-memory fallback map — prevents repeated Mongo hits during warm-up gap
    private final ConcurrentHashMap<String, String> localFallback = new ConcurrentHashMap<>();

    public String getHomeId(String deviceId) {
        String key = PREFIX + deviceId;

        // 1. Redis hit — fast path, ~0.1ms
        String cached = stringRedisTemplate.opsForValue().get(key);
        if (cached != null) return cached;

        // 2. Local in-memory fallback — covers the gap between startup and cache warm
        String local = localFallback.get(deviceId);
        if (local != null) return local;

        // 3. Cache miss — load from Mongo on a background thread, return null for NOW
        // The NEXT message from this device (within ms on live data) will hit Redis
        CompletableFuture.runAsync(() -> {
            deviceRepo.findById(deviceId).ifPresent(device -> {
                if (device.getHomeId() != null) {
                    stringRedisTemplate.opsForValue().set(key, device.getHomeId(), TTL);
                    localFallback.put(deviceId, device.getHomeId());
                    log.info("Cache miss resolved for device {}", deviceId);
                }
            });
        }, taskExecutor);

        return null; // this message is dropped — next one will hit cache
    }

    @PostConstruct
    public void warmCache() {
        try {
            List<Device> devices = deviceRepo.findAll();

            // Also populate local fallback so first messages before Redis warms don't miss
            devices.stream()
                    .filter(d -> d.getHomeId() != null)
                    .forEach(d -> localFallback.put(d.getId(), d.getHomeId()));

            stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                devices.stream()
                        .filter(d -> d.getHomeId() != null)
                        .forEach(d -> {
                            byte[] k = (PREFIX + d.getId()).getBytes();
                            byte[] v = d.getHomeId().getBytes();
                            connection.stringCommands().setEx(k, TTL.getSeconds(), v);
                        });
                return null;
            });

            log.info("DeviceHomeCache warmed: {} devices", devices.size());
        } catch (Exception e) {
            log.warn("Cache warm failed: {}", e.getMessage());
        }
    }

    public void put(String deviceId, String homeId) {
        localFallback.put(deviceId, homeId);
        stringRedisTemplate.opsForValue().set(PREFIX + deviceId, homeId, TTL);
    }

    public void evict(String deviceId) {
        localFallback.remove(deviceId);
        stringRedisTemplate.delete(PREFIX + deviceId);
    }
}