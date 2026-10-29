package dev.automata.automata.utils;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TtlCacheTest {

    private final AtomicLong now = new AtomicLong();

    private TtlCache<String, String> cache(long ttlSeconds, int maxSize) {
        return new TtlCache<>(TimeUnit.SECONDS.toNanos(ttlSeconds), maxSize, now::get);
    }

    private void advanceSeconds(long s) {
        now.addAndGet(TimeUnit.SECONDS.toNanos(s));
    }

    @Test
    void loadsOnceThenServesFromCache() {
        TtlCache<String, String> c = cache(10, 100);
        AtomicInteger loads = new AtomicInteger();
        assertEquals("v", c.get("k", () -> {
            loads.incrementAndGet();
            return "v";
        }));
        assertEquals("v", c.get("k", () -> {
            loads.incrementAndGet();
            return "other";
        }));
        assertEquals(1, loads.get());
    }

    @Test
    void entriesExpireAfterTheTtlButNotBefore() {
        TtlCache<String, String> c = cache(10, 100);
        AtomicInteger loads = new AtomicInteger();
        c.get("k", () -> "v" + loads.incrementAndGet());
        advanceSeconds(9);
        assertEquals("v1", c.get("k", () -> "v" + loads.incrementAndGet()));   // still valid
        advanceSeconds(2);                                                      // 11s > ttl
        assertEquals("v2", c.get("k", () -> "v" + loads.incrementAndGet()));
        assertEquals(2, loads.get());
    }

    @Test
    void invalidateForcesAReload() {
        TtlCache<String, String> c = cache(10, 100);
        AtomicInteger loads = new AtomicInteger();
        c.get("k", () -> "v" + loads.incrementAndGet());
        c.invalidate("k");
        assertEquals("v2", c.get("k", () -> "v" + loads.incrementAndGet()));
    }

    @Test
    void aFailingLoaderIsNotCachedAndTheNextCallRetries() {
        TtlCache<String, String> c = cache(10, 100);
        assertThrows(IllegalStateException.class, () -> c.get("k", () -> {
            throw new IllegalStateException("db down");
        }));
        assertEquals("ok", c.get("k", () -> "ok"));
    }

    @Test
    void aNullResultIsNotCached() {
        TtlCache<String, String> c = cache(10, 100);
        assertNull(c.get("k", () -> null));
        assertEquals("v", c.get("k", () -> "v"));
    }

    @Test
    void concurrentMissesRunTheLoaderOnlyOnce() throws Exception {
        TtlCache<String, String> c = cache(60, 100);
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch go = new CountDownLatch(1);
        List<String> results = java.util.Collections.synchronizedList(new ArrayList<>());
        Thread[] ts = new Thread[40];
        for (int i = 0; i < ts.length; i++) {
            ts[i] = new Thread(() -> {
                try {
                    go.await();
                } catch (InterruptedException e) {
                    return;
                }
                results.add(c.get("k", () -> {
                    loads.incrementAndGet();
                    try {
                        Thread.sleep(150);
                    } catch (InterruptedException ignored) {
                    }
                    return "value";
                }));
            });
            ts[i].start();
        }
        go.countDown();
        for (Thread t : ts) t.join();
        assertEquals(1, loads.get(), "loader should run once, ran " + loads.get());
        assertEquals(40, results.size());
        assertTrue(results.stream().allMatch("value"::equals));
    }

    @Test
    void waitersSeeTheLeadersFailureWithoutRunningTheLoaderAgain() throws Exception {
        TtlCache<String, String> c = cache(60, 100);
        AtomicInteger loads = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        CountDownLatch go = new CountDownLatch(1);
        Thread[] ts = new Thread[10];
        for (int i = 0; i < ts.length; i++) {
            ts[i] = new Thread(() -> {
                try {
                    go.await();
                    c.get("k", () -> {
                        loads.incrementAndGet();
                        try {
                            Thread.sleep(300);
                        } catch (InterruptedException ignored) {
                        }
                        throw new IllegalStateException("db down");
                    });
                } catch (IllegalStateException e) {
                    failures.incrementAndGet();
                } catch (InterruptedException ignored) {
                }
            });
            ts[i].start();
        }
        go.countDown();
        for (Thread t : ts) t.join();
        assertEquals(1, loads.get());
        assertEquals(10, failures.get());
    }

    @Test
    void sizeStaysBounded() {
        TtlCache<String, String> c = cache(60, 50);
        for (int i = 0; i < 500; i++) c.get("k" + i, () -> "v");
        assertTrue(c.size() <= 50, "size=" + c.size());
    }

    @Test
    void expiredEntriesAreEvictedBeforeLiveOnes() {
        TtlCache<String, String> c = cache(10, 3);
        c.put("a", "1");
        c.put("b", "2");
        advanceSeconds(11);          // a and b are now expired
        c.put("c", "3");
        c.put("d", "4");
        c.put("e", "5");             // 5 entries > max 3 -> expired ones go first
        assertTrue(c.getIfPresent("c") != null && c.getIfPresent("d") != null && c.getIfPresent("e") != null);
        assertNull(c.getIfPresent("a"));
    }
}
