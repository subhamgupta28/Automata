package dev.automata.automata.model;


import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "dashboard")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class Dashboard {

    @Id
    private String id;

    private String deviceId;
    private double x;
    private double y;
    private boolean showCharts;
    private boolean analytics;
    private boolean showInDashboard;
    private String homeId;
    @Indexed
    @LastModifiedDate
    private Instant updateDate;
}
