package dev.automata.automata.model;


import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "attributeType")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class AttributeType {
    @Id
    private String id;
    private String name;
    private String description;
    private String value;

    @Indexed(unique = true)
    private String type;
    @Indexed
    @LastModifiedDate
    private Instant updateDate;
}
