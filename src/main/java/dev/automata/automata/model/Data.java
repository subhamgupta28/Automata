package dev.automata.automata.model;


import lombok.*;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

@Document(collection = "data")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Data {
    @Id
    private String id;
    private String deviceId;
    private Map<String, Object> data;
    private Long timestamp;
}
