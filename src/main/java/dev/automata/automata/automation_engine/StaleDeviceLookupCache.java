package dev.automata.automata.automation_engine;

import dev.automata.automata.model.Data;
import dev.automata.automata.service.MainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Guards the one Mongo call that can run synchronously inside a live
 * automation evaluation tick: MainService.getLastFullData(), used by
 * AutomationEvaluator.resolveSecondaryPayload() as the DB fallback for
 * "stale" conditions when Redis has no recent data for the device.
 * <p>
 * Two problems this solves:
 * <p>
 * 1. THUNDERING HERD — if a device goes offline, every automation with a
 * "stale" condition on that device will independently hit Mongo for the
 * same deviceId on every tick, all within the same window. A short-TTL
 * cache collapses that into one Mongo call per TTL window per device,
 * regardless of how many automations or ticks ask for it.
 * <p>
 * 2. UNBOUNDED BLOCKING — evaluate() runs on the automationExecutor pool
 * (2-4 threads, CallerRunsPolicy). A slow/hanging Mongo call there can
 * stall a large fraction of the automation engine's capacity, or even
 * the thread that received the triggering device message. Lookups here
 * run on a separate executor with a hard timeout; on timeout or failure
 * we return the same "unresolvable" signal the evaluator already knows
 * how to handle (treats last_seen as unresolvable → STALE), so behavior
 * degrades safely instead of hanging.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StaleDeviceLookupCache {

    private final MainService mainService;
    private final ThreadPoolTaskExecutor taskExecutor;

    private static final long CACHE_TTL_MS = 20_000;   // matches typical eval-tick cadence
    private static final long LOOKUP_TIMEOUT_MS = 250;  // bounded — never let Mongo stall an eval thread

    private record CacheEntry(Data data, long fetchedAtMs) {
    }

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    // De-dupes concurrent misses for the same deviceId so N automations
    // asking about the same offline device in the same instant only
    // trigger ONE Mongo call, not N.
    private final ConcurrentHashMap<String, CompletableFuture<Data>> inFlight = new ConcurrentHashMap<>();

    /**
     * Returns the last known Data for deviceId, or null if it cannot be
     * resolved within the timeout. Never blocks longer than
     * LOOKUP_TIMEOUT_MS regardless of how slow Mongo is.
     */
    public Data getLastFullData(String deviceId) {
        CacheEntry cached = cache.get(deviceId);
        if (cached != null && System.currentTimeMillis() - cached.fetchedAtMs() < CACHE_TTL_MS) {
            return cached.data();
        }

        CompletableFuture<Data> future = inFlight.computeIfAbsent(deviceId, id ->
                CompletableFuture.supplyAsync(() -> mainService.getLastFullData(id), taskExecutor)
                        .whenComplete((data, err) -> {
                            inFlight.remove(id);
                            if (err == null && data != null) {
                                cache.put(id, new CacheEntry(data, System.currentTimeMillis()));
                            }
                        }));

        try {
            return future.get(LOOKUP_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.warn("⏱️ Stale-device DB lookup for '{}' exceeded {}ms — treating as unresolvable this tick",
                    deviceId, LOOKUP_TIMEOUT_MS);
            // Stale entry (if any) can still serve the NEXT caller once the
            // in-flight future eventually completes; this caller gets null now.
            return cached != null ? cached.data() : null;
        } catch (Exception e) {
            log.error("❌ Stale-device DB lookup failed for '{}': {}", deviceId, e.getMessage());
            return cached != null ? cached.data() : null;
        }
    }
}