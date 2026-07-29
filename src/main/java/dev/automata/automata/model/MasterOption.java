package dev.automata.automata.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class MasterOption {
    @Id
    private String id;
    private String deviceId;
    private String key;
    private String name;
    @Indexed
    @LastModifiedDate
    private Instant updateDate;
}
