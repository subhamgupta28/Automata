package dev.automata.automata.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Notification {
    @Id
    private String id;
    private String message;
    private String automationId;
    private String severity;   // "info" | "success" | "warning" | "error" | "automation"
    private String header;     // new — maps to snackbar title
    @Indexed
    private String timestamp;
    @Indexed
    @LastModifiedDate
    private Instant updateDate;
}
