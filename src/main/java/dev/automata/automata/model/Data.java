package dev.automata.automata.model;


import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document(collection = "data")
@CompoundIndexes({
        // "latest reading for a device" (getFirst…OrderByTimestampDesc) — avoids an in-memory sort of the device's history
        @CompoundIndex(name = "device_timestamp_idx", def = "{'deviceId': 1, 'timestamp': -1}"),
        // analytics range queries (findByDeviceIdAndUpdateDateBetween, weekly aggregation)
        @CompoundIndex(name = "device_updateDate_idx", def = "{'deviceId': 1, 'updateDate': -1}")
})
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class Data {
    @Id
    private String id;

    @Indexed
    private String deviceId;
    private String dateTime;
    private Map<String, Object> data;

    private Long timestamp;

    @Indexed
    @LastModifiedDate
    private Instant updateDate;

    /**
     * When MongoDB's TTL monitor may delete this reading (TTL index with expireAfter=0: the document goes away once
     * this instant passes). null = keep forever; readings written before TTL was introduced have no value here.
     * A dedicated field is used because {@code updateDate} already carries a plain index, and MongoDB refuses a
     * TTL index on the same key (index options conflict). Retention: {@code automata.data.retention-days}.
     */
    @Indexed(expireAfter = "0s")
    private Instant expireAt;
}
