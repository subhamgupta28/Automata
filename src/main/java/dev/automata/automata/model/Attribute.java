package dev.automata.automata.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

@Document(collection = "attribute")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
@JsonIgnoreProperties(ignoreUnknown = true)
public class Attribute {
    @Id
    private String id;
    private String displayName;
    private String key;
    private String units;
    private Boolean visible = true;
    private String type;
    private Map<String, Object> extras;
    private String deviceId;
    @Indexed
    @LastModifiedDate
    private Instant updateDate;
}

