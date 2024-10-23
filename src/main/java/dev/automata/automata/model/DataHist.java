package dev.automata.automata.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.Map;

@Document(collection = "data_hist")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class DataHist {
    @Id
    private String id;
    private String deviceId;
    private Map<String, Object> data;
    private Date lastUpdated;
}
