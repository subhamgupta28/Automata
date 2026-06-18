package dev.automata.automata.service;

import dev.automata.automata.repository.DeviceRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class DeviceHomeCache {

    private final RedisTemplate<String, Object> redisTemplate;
    private final DeviceRepository deviceRepo;

    private static final String PREFIX = "device:home:";
    private static final Duration TTL = Duration.ofHours(12);

    /**
     * Returns homeId for a deviceId. Hits Redis first, falls back to MongoDB.
     * Call this on every inbound MQTT message.
     */
    public String getHomeId(String deviceId) {
        String key = PREFIX + deviceId;
        var cached = redisTemplate.opsForValue().get(key);
        if (cached != null) return cached.toString();

        // Cache miss — load from MongoDB and cache it
        return deviceRepo.findById(deviceId).map(device -> {
            if (device.getHomeId() != null) {
                redisTemplate.opsForValue().set(key, device.getHomeId(), TTL);
                return device.getHomeId();
            }
            return null;
        }).orElse(null);
    }

    /**
     * Call this when a device is moved to a different home.
     */
    public void evict(String deviceId) {
        redisTemplate.delete(PREFIX + deviceId);
    }

    /**
     * Warm the cache on startup for all known devices.
     */
    @PostConstruct
    public void warmCache() {
        deviceRepo.findAll().forEach(d -> {
            if (d.getHomeId() != null)
                redisTemplate.opsForValue().set(
                        PREFIX + d.getId(), d.getHomeId(), TTL);
        });
    }

    public void put(String deviceId, String homeId) {
        redisTemplate.opsForValue().set(PREFIX + deviceId, homeId, TTL);
    }
}
