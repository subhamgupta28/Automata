package dev.automata.automata.model;


import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "parameter")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class Parameter {
    @Id
    private String id;
    private String deviceId;
    private Long transactionFrom;
    private Long transactionTo;
    @Indexed
    @LastModifiedDate
    private Instant updateDate;
}
