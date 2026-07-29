package dev.automata.automata.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "deviceCharts")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class DeviceCharts {

    @Id
    private String id;
    private String deviceId;
    private boolean showChart;
    private String attributeKey;
    private String homeId;
    @Indexed
    @LastModifiedDate
    private Instant updateDate;
}
