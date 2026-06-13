package dev.automata.automata.repository;

import dev.automata.automata.model.RecordingBucket;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Date;
import java.util.List;

public interface RecordingBucketRepository extends MongoRepository<RecordingBucket, String> {

    List<RecordingBucket> findBySessionIdAndDeviceIdAndBucketStartBetween(
            String sessionId, String deviceId, Date from, Date to);

    List<RecordingBucket> findBySessionIdOrderByBucketStartAsc(String sessionId);

    List<RecordingBucket> findBySessionIdAndDeviceIdOrderByBucketStartAsc(
            String sessionId, String deviceId);

    /**
     * Count total buckets for a session (for size estimation)
     */
    long countBySessionId(String sessionId);

    /**
     * Paged replay — latest N buckets for a device
     */
    List<RecordingBucket> findBySessionIdAndDeviceIdOrderByBucketStartDesc(
            String sessionId, String deviceId, Pageable pageable);

    void deleteBySessionId(String sessionId);
}