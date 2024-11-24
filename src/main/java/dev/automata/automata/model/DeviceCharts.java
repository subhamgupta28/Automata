package dev.automata.automata.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

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
}
