package dev.automata.automata.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

/**
 * Bounded write-behind buffer that turns many small writes into a few batched ones.
 *
 * <ul>
 *   <li>{@link #submit} is O(1) and never blocks on I/O in the normal case.</li>
 *   <li>One daemon worker drains the queue into batches of up to {@code maxBatch}, waiting at most
 *       {@code lingerMs} for a batch to fill, so added latency is bounded by {@code lingerMs}.</li>
 *   <li><b>Back-pressure, not data loss:</b> when the queue is full the <em>caller</em> performs the write
 *       synchronously (same cost as having no buffer), so memory stays bounded and nothing is dropped.</li>
 *   <li>A failing batch is retried {@code maxAttempts} times with a growing delay, then dropped and reported.
 *       Make the sink idempotent (e.g. pre-assigned ids) so a retry after a partial success is harmless.</li>
 *   <li>{@link #close} stops the worker and writes everything still queued.</li>
 * </ul>
 *
 * Durability trade-off: items that are queued but not yet written are lost if the JVM is killed
 * ({@code kill -9}, OOM, power loss). A normal shutdown flushes them.
 *
 * No framework dependencies on purpose, so it can be unit-tested in isolation.
 */
public final class BatchWriter<T> implements AutoCloseable {

    @FunctionalInterface
    public interface Sink<T> {
        void write(List<T> batch) throws Exception;
    }

    public record Stats(long enqueued, long written, long batches, long overflowWrites,
                        long dropped, long retries, int queued) {
    }

    private static final long WAKE_SLICE_NANOS = TimeUnit.MILLISECONDS.toNanos(20);

    private final String name;
    private final BlockingQueue<T> queue;
    private final int maxBatch;
    private final long lingerNanos;
    private final int maxAttempts;
    private final long retryBackoffMs;
    private final Sink<T> sink;
    private final BiConsumer<String, Throwable> onError;
    private final Thread worker;

    private volatile boolean running = true;

    private final AtomicLong enqueued = new AtomicLong();
    private final AtomicLong written = new AtomicLong();
    private final AtomicLong batches = new AtomicLong();
    private final AtomicLong overflowWrites = new AtomicLong();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong retries = new AtomicLong();

    public BatchWriter(String name, int capacity, int maxBatch, long lingerMs,
                       int maxAttempts, long retryBackoffMs,
                       Sink<T> sink, BiConsumer<String, Throwable> onError) {
        if (capacity < 1 || maxBatch < 1 || maxAttempts < 1) {
            throw new IllegalArgumentException("capacity, maxBatch and maxAttempts must be >= 1");
        }
        this.name = name;
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.maxBatch = maxBatch;
        this.lingerNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(0, lingerMs));
        this.maxAttempts = maxAttempts;
        this.retryBackoffMs = Math.max(0, retryBackoffMs);
        this.sink = sink;
        this.onError = onError != null ? onError : (m, t) -> System.err.println(m + (t != null ? ": " + t : ""));
        this.worker = new Thread(this::run, name + "-batch-writer");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    /** Queue the item. Falls back to a synchronous write by the caller when the queue is full or closed. */
    public void submit(T item) {
        if (running && queue.offer(item)) {
            enqueued.incrementAndGet();
            return;
        }
        overflowWrites.incrementAndGet();
        try {
            sink.write(List.of(item));
            written.incrementAndGet();
            batches.incrementAndGet();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Synchronously writes whatever is queued right now (used on shutdown and in tests). */
    public void flush() {
        drainAll();
    }

    public Stats stats() {
        return new Stats(enqueued.get(), written.get(), batches.get(), overflowWrites.get(),
                dropped.get(), retries.get(), queue.size());
    }

    @Override
    public void close() {
        running = false;
        try {
            worker.join(15_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        drainAll();   // anything the worker did not get to
    }

    // ────────────────────────────── worker ──────────────────────────────

    private void run() {
        List<T> batch = new ArrayList<>(maxBatch);
        while (true) {
            try {
                T first = queue.poll(WAKE_SLICE_NANOS, TimeUnit.NANOSECONDS);
                if (first == null) {
                    if (!running) break;
                    continue;
                }
                batch.add(first);
                long deadline = System.nanoTime() + lingerNanos;
                while (batch.size() < maxBatch) {
                    T next = queue.poll();
                    if (next != null) {
                        batch.add(next);
                        continue;
                    }
                    long left = deadline - System.nanoTime();
                    if (left <= 0 || !running) break;
                    next = queue.poll(Math.min(left, WAKE_SLICE_NANOS), TimeUnit.NANOSECONDS);
                    if (next != null) batch.add(next);
                }
                writeWithRetry(batch);
            } catch (InterruptedException e) {
                if (!batch.isEmpty()) writeWithRetry(batch);
                Thread.currentThread().interrupt();
                break;
            } finally {
                batch.clear();
            }
        }
        drainAll();
    }

    private void drainAll() {
        while (!queue.isEmpty()) {
            List<T> batch = new ArrayList<>(maxBatch);
            queue.drainTo(batch, maxBatch);
            if (batch.isEmpty()) break;
            writeWithRetry(batch);
        }
    }

    private void writeWithRetry(List<T> batch) {
        // the sink may keep a reference to the list it is given, so hand it a private copy
        List<T> copy = List.copyOf(batch);
        for (int attempt = 1; ; attempt++) {
            try {
                sink.write(copy);
                written.addAndGet(copy.size());
                batches.incrementAndGet();
                return;
            } catch (Exception e) {
                if (attempt >= maxAttempts) {
                    dropped.addAndGet(copy.size());
                    onError.accept(name + ": dropping " + copy.size() + " item(s) after " + attempt + " attempt(s)", e);
                    return;
                }
                retries.incrementAndGet();
                try {
                    Thread.sleep(retryBackoffMs * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    dropped.addAndGet(copy.size());
                    onError.accept(name + ": interrupted while retrying, dropping " + copy.size() + " item(s)", ie);
                    return;
                }
            }
        }
    }
}
