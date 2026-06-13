package dev.automata.automata.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Bucketing pattern: instead of 1 document per reading (60/min per device),
 * we store up to BUCKET_SIZE readings per document.
 * <p>
 * One document = one device, one 60-second window.
 * Index: sessionId + deviceId + bucketStart  → fast range queries for replay.
 * <p>
 * MongoDB 6 supports native Time Series collections, but the bucketing pattern
 * gives us more control over flush granularity and works with existing indexes.
 */
@lombok.Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "recording_data")
@CompoundIndexes({
        @CompoundIndex(name = "session_device_time",
                def = "{'sessionId': 1, 'deviceId': 1, 'bucketStart': 1}"),
        @CompoundIndex(name = "session_time",
                def = "{'sessionId': 1, 'bucketStart': 1}")
})
public class RecordingBucket {

    public static final int BUCKET_SIZE = 60; // readings per bucket

    @Id
    private String id;

    private String sessionId;
    private String deviceId;

    /**
     * UTC start of the 60-second window this bucket covers
     */
    private Date bucketStart;
    /**
     * UTC end of the window (bucketStart + 60 s)
     */
    private Date bucketEnd;

    /**
     * Number of readings currently in this bucket
     */
    private int count;

    /**
     * The actual readings.
     * Each entry is a raw Map matching whatever the device publishes,
     * plus a guaranteed "ts" key (epoch-ms long).
     */
    @Builder.Default
    private List<Map<String, Object>> readings = new ArrayList<>();
}