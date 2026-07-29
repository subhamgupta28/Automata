package dev.automata.automata.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "featureToggle")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class FeatureToggle {
    @Id
    private String id;
    private boolean isEnabled;
    private String description;
    private String featureKey;
    private String env;
    private String group;
    private String type;
    @Indexed
    @LastModifiedDate
    private Instant updateDate;
}
