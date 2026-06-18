package dev.automata.automata.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "homes")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Home {

    @Id
    private String id;
    private String name;
    private String ownerId;          // denormalized for fast owner checks
    private String timezone;
    private Instant createdAt;
}