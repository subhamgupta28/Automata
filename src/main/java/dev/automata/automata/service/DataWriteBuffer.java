package dev.automata.automata.service;

import com.mongodb.MongoBulkWriteException;
import com.mongodb.client.model.InsertManyOptions;
import dev.automata.automata.model.Data;
import dev.automata.automata.utils.BatchWriter;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Write-behind buffer for the high-rate device data path (MQTT {@code sendData}).
 *
 * <p>Every message used to cost one synchronous Mongo insert on a small thread pool. Messages are now queued and
 * written with one {@code insertMany} per batch (up to {@code max-size} documents, waiting at most {@code linger-ms}).
 *
 * <p>Design points that matter for correctness:
 * <ul>
 *   <li><b>Converted on the caller's thread.</b> The reading is mapped to BSON before it is queued, so a message that
 *       cannot be mapped fails alone — exactly like the old per-message save — and can never poison a batch. It also
 *       means the writer thread never touches the caller's mutable payload map.</li>
 *   <li><b>Ids are assigned up front</b> and the insert is <em>unordered</em>, so a retry after a partial failure is
 *       idempotent: already-written documents come back as duplicate-key (11000) and are ignored.</li>
 *   <li><b>Back-pressure, not loss:</b> if the queue is full the calling thread inserts synchronously.</li>
 *   <li><b>Durability trade-off:</b> readings still queued when the JVM is killed (not a normal shutdown) are lost —
 *       at most about {@code linger-ms} of data. A normal shutdown flushes everything.</li>
 * </ul>
 *
 * <p>Properties (all optional): {@code automata.data.batch.enabled=true}, {@code .max-size=500}, {@code .linger-ms=200},
 * {@code .queue-capacity=20000}. Set {@code enabled=false} to get the previous synchronous behaviour.
 */
@Slf4j
@Component
public class DataWriteBuffer {

    private static final int DUPLICATE_KEY = 11000;

    private final MongoTemplate mongo;
    private final String collection;
    private final BatchWriter<Document> writer;   // null when batching is disabled

    public DataWriteBuffer(MongoTemplate mongo,
                           @Value("${automata.data.batch.enabled:true}") boolean enabled,
                           @Value("${automata.data.batch.max-size:500}") int maxBatch,
                           @Value("${automata.data.batch.linger-ms:200}") long lingerMs,
                           @Value("${automata.data.batch.queue-capacity:20000}") int capacity,
                           ObjectProvider<MeterRegistry> registry) {
        this.mongo = mongo;
        this.collection = mongo.getCollectionName(Data.class);
        this.writer = enabled
                ? new BatchWriter<>("data", capacity, maxBatch, lingerMs, 3, 200,
                this::insertBatch, (message, error) -> log.error("❌ {}", message, error))
                : null;

        if (writer == null) {
            log.info("📦 Data write buffer disabled — every reading is inserted synchronously");
            return;
        }
        registry.ifAvailable(this::bindMetrics);
        log.info("📦 Data write buffer enabled: batches of up to {} readings, linger {} ms, queue {}",
                maxBatch, lingerMs, capacity);
    }

    /** Queue a reading for insertion (or insert it right away when batching is disabled). */
    public void add(Data data) {
        if (writer == null) {
            mongo.insert(data);
            return;
        }
        if (data.getId() == null) data.setId(new ObjectId().toHexString());
        Document document = new Document();
        mongo.getConverter().write(data, document);   // may throw for an unmappable payload — on the caller's thread
        writer.submit(document);
    }

    public BatchWriter.Stats stats() {
        return writer == null ? null : writer.stats();
    }

    @PreDestroy
    public void shutdown() {
        if (writer == null) return;
        writer.close();   // flushes whatever is still queued
        log.info("📦 Data write buffer closed: {}", writer.stats());
    }

    private void insertBatch(List<Document> batch) {
        try {
            mongo.getCollection(collection).insertMany(batch, new InsertManyOptions().ordered(false));
        } catch (MongoBulkWriteException e) {
            // Unordered: every document that could be written, was. 11000 = already written by an earlier attempt.
            long rejected = e.getWriteErrors().stream().filter(we -> we.getCode() != DUPLICATE_KEY).count();
            if (rejected > 0) {
                log.error("❌ MongoDB rejected {} of {} reading(s); they will not be retried: {}",
                        rejected, batch.size(), e.getWriteErrors().get(0).getMessage());
            }
            // deliberately not rethrown: there is nothing left to retry
        }
        // any other exception (network, timeout, …) propagates → BatchWriter retries the whole, idempotent batch
    }

    private void bindMetrics(MeterRegistry registry) {
        Gauge.builder("automata.data.buffer.queued", writer, w -> w.stats().queued()).register(registry);
        FunctionCounter.builder("automata.data.buffer.written", writer, w -> w.stats().written()).register(registry);
        FunctionCounter.builder("automata.data.buffer.batches", writer, w -> w.stats().batches()).register(registry);
        FunctionCounter.builder("automata.data.buffer.overflow", writer, w -> w.stats().overflowWrites()).register(registry);
        FunctionCounter.builder("automata.data.buffer.dropped", writer, w -> w.stats().dropped()).register(registry);
    }
}
