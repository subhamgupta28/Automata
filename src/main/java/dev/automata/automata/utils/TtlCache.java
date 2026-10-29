package dev.automata.automata.utils;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.function.LongSupplier;

/**
 * Small thread-safe cache with a time-to-live and a size bound.
 *
 * <ul>
 *   <li><b>Single-flight loading:</b> when an entry is missing or expired and many threads ask for it at once,
 *       one thread runs the loader and the others wait for its result. Without this, every expiry at high request
 *       rates would send a burst of identical queries to the database.</li>
 *   <li>The loader runs <em>outside</em> any map lock, so slow I/O in the loader never blocks other keys
 *       (and never pins a virtual thread inside a synchronized map bin).</li>
 *   <li>Loader exceptions and {@code null} results are never cached.</li>
 *   <li>The size bound is enforced on insert: expired entries go first, then arbitrary ones.</li>
 * </ul>
 *
 * Values should be immutable, or copied by the caller on read.
 */
public final class TtlCache<K, V> {

    private record Entry<V>(V value, long expiresAtNanos) {
    }

    private final ConcurrentHashMap<K, Entry<V>> map = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<K, CompletableFuture<V>> inflight = new ConcurrentHashMap<>();
    private final long ttlNanos;
    private final int maxSize;
    private final LongSupplier nanoClock;

    public TtlCache(long ttlNanos, int maxSize) {
        this(ttlNanos, maxSize, System::nanoTime);
    }

    /** The clock is injectable so tests can move time without sleeping. */
    public TtlCache(long ttlNanos, int maxSize, LongSupplier nanoClock) {
        if (ttlNanos <= 0 || maxSize < 1) throw new IllegalArgumentException("ttl and maxSize must be positive");
        this.ttlNanos = ttlNanos;
        this.maxSize = maxSize;
        this.nanoClock = nanoClock;
    }

    /** @return the cached value, or null when absent/expired (does not load). */
    public V getIfPresent(K key) {
        Entry<V> e = map.get(key);
        return e != null && e.expiresAtNanos() - nanoClock.getAsLong() > 0 ? e.value() : null;
    }

    public V get(K key, Supplier<? extends V> loader) {
        V hit = getIfPresent(key);
        if (hit != null) return hit;

        CompletableFuture<V> mine = new CompletableFuture<>();
        CompletableFuture<V> leader = inflight.putIfAbsent(key, mine);
        if (leader != null) {                     // someone else is already loading this key
            try {
                return leader.join();
            } catch (CompletionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof RuntimeException re) throw re;
                if (cause instanceof Error err) throw err;
                throw e;
            }
        }
        try {
            V loaded = loader.get();
            if (loaded != null) put(key, loaded);
            mine.complete(loaded);
            return loaded;
        } catch (RuntimeException | Error e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inflight.remove(key, mine);
        }
    }

    public void put(K key, V value) {
        map.put(key, new Entry<>(value, nanoClock.getAsLong() + ttlNanos));
        if (map.size() > maxSize) evict();
    }

    public void invalidate(K key) {
        map.remove(key);
    }

    public void clear() {
        map.clear();
    }

    public int size() {
        return map.size();
    }

    private void evict() {
        long now = nanoClock.getAsLong();
        map.entrySet().removeIf(en -> en.getValue().expiresAtNanos() - now <= 0);
        if (map.size() <= maxSize) return;
        int target = Math.max(1, (int) (maxSize * 0.9));
        Iterator<Map.Entry<K, Entry<V>>> it = map.entrySet().iterator();
        while (map.size() > target && it.hasNext()) {
            it.next();
            it.remove();
        }
    }
}
