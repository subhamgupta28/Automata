package dev.automata.automata.dto;

import lombok.*;

@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Notification {
    private String message;
    private String severity;
}
