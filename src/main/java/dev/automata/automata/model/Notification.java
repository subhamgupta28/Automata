package dev.automata.automata.model;

import lombok.*;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

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
    private String severity;

    @Indexed
    private Date timestamp;

}
