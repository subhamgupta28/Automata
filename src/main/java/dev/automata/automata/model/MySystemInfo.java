package dev.automata.automata.model;

import lombok.*;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.Instant;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class MySystemInfo {
    private String processor;
    private Integer totalMemorySize;
    private Integer availableMemorySize;
    private String logicalProcessor;
    private String physicalProcessor;
    @Indexed
    @LastModifiedDate
    private Instant updateDate;
}
