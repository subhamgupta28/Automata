package dev.automata.automata.model;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import lombok.*;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.UUID;

@Document(collection = "attribute")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class Attribute {
    @Id
    private String id;
    private String displayName;
    private String key;
    private String units;
    private String deviceId;
    private Long timestamp;
}

