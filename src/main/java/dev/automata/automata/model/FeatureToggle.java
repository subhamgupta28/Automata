package dev.automata.automata.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

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
}
