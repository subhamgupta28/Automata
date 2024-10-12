package dev.automata.automata.model;

import lombok.*;

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
}
