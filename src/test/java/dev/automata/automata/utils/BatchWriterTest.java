package dev.automata.automata.utils;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchWriterTest {

    private static final BiConsumer<String, Throwable> QUIET = (m, t) -> {
    };

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long end = System.nanoTime() + 5_000_000_000L;
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > end) throw new AssertionError("condition not met within 5s");
            Thread.sleep(5);
        }
    }

    @Test
    void writesEverythingInBatchesNeverLargerThanTheMaximum() {
        List<Integer> sizes = Collections.synchronizedList(new ArrayList<>());
        Set<Integer> seen = ConcurrentHashMap.newKeySet();
        try (BatchWriter<Integer> w = new BatchWriter<>("t", 10_000, 100, 20, 3, 1,
                batch -> {
                    sizes.add(batch.size());
                    seen.addAll(batch);
                }, QUIET)) {
            for (int i = 0; i < 1000; i++) w.submit(i);
        }
        assertEquals(1000, seen.size());
        assertTrue(sizes.stream().allMatch(s -> s <= 100), "a batch exceeded maxBatch: " + sizes);
        assertTrue(sizes.stream().anyMatch(s -> s > 1), "nothing was batched: " + sizes);
    }

    @Test
    void lingerFlushesAPartialBatchWithoutWaitingForItToFill() throws Exception {
        Set<Integer> seen = ConcurrentHashMap.newKeySet();
        try (BatchWriter<Integer> w = new BatchWriter<>("t", 100, 500, 30, 3, 1, seen::addAll, QUIET)) {
            w.submit(1);
            w.submit(2);
            w.submit(3);
            await(() -> seen.size() == 3);
        }
    }

    @Test
    void closeFlushesQueuedItemsPromptlyEvenWithALongLinger() {
        Set<Integer> seen = ConcurrentHashMap.newKeySet();
        BatchWriter<Integer> w = new BatchWriter<>("t", 100, 500, 5_000, 3, 1, seen::addAll, QUIET);
        for (int i = 0; i < 5; i++) w.submit(i);
        long t0 = System.nanoTime();
        w.close();
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertEquals(5, seen.size());
        assertTrue(ms < 2_000, "close() took " + ms + " ms");
    }

    @Test
    void concurrentProducersLoseNothingAndDuplicateNothing() throws Exception {
        ConcurrentHashMap<Integer, Integer> counts = new ConcurrentHashMap<>();
        int threads = 8, perThread = 5_000;
        BatchWriter<Integer> w = new BatchWriter<>("t", 100_000, 250, 10, 3, 1,
                batch -> batch.forEach(i -> counts.merge(i, 1, Integer::sum)), QUIET);
        Thread[] producers = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            final int base = t * perThread;
            producers[t] = new Thread(() -> {
                for (int i = 0; i < perThread; i++) w.submit(base + i);
            });
            producers[t].start();
        }
        for (Thread p : producers) p.join();
        w.close();
        assertEquals(threads * perThread, counts.size());
        assertTrue(counts.values().stream().allMatch(c -> c == 1), "an item was written more than once");
        assertEquals(0, w.stats().dropped());
    }

    @Test
    void aFullQueueMakesTheCallerWriteSynchronouslyInsteadOfDropping() {
        CountDownLatch release = new CountDownLatch(1);
        Set<Integer> seen = ConcurrentHashMap.newKeySet();
        BatchWriter<Integer> w = new BatchWriter<>("t", 2, 1, 0, 1, 0, batch -> {
            // stall only the worker thread, so the queue backs up while callers can still write
            if (Thread.currentThread().getName().endsWith("batch-writer")) {
                try {
                    release.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            seen.addAll(batch);
        }, QUIET);
        for (int i = 0; i < 20; i++) w.submit(i);
        assertTrue(w.stats().overflowWrites() > 0, "expected back-pressure writes: " + w.stats());
        release.countDown();
        w.close();
        assertEquals(20, seen.size());
        assertEquals(0, w.stats().dropped());
    }

    @Test
    void retriesATransientFailureThenSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        Set<Integer> seen = ConcurrentHashMap.newKeySet();
        BatchWriter<Integer> w = new BatchWriter<>("t", 100, 100, 5, 3, 1, batch -> {
            if (calls.incrementAndGet() <= 2) throw new IllegalStateException("db down");
            seen.addAll(batch);
        }, QUIET);
        w.submit(1);
        w.submit(2);
        w.close();
        assertEquals(2, seen.size());
        assertEquals(0, w.stats().dropped());
        assertTrue(w.stats().retries() >= 2, "retries=" + w.stats().retries());
    }

    @Test
    void dropsABatchAfterTheMaximumAttemptsAndReportsIt() {
        AtomicReference<String> reported = new AtomicReference<>();
        BatchWriter<Integer> w = new BatchWriter<>("t", 100, 100, 5, 2, 1,
                batch -> {
                    throw new IllegalStateException("always failing");
                },
                (message, error) -> reported.set(message));
        w.submit(1);
        w.submit(2);
        w.submit(3);
        w.close();
        assertEquals(3, w.stats().dropped());
        assertTrue(reported.get() != null && reported.get().contains("dropping"), "reported: " + reported.get());
    }

    @Test
    void submitAfterCloseStillWritesSynchronously() {
        Set<Integer> seen = ConcurrentHashMap.newKeySet();
        BatchWriter<Integer> w = new BatchWriter<>("t", 10, 10, 5, 1, 1, seen::addAll, QUIET);
        w.close();
        w.submit(42);
        assertTrue(seen.contains(42));
        assertEquals(1, w.stats().overflowWrites());
    }
}
