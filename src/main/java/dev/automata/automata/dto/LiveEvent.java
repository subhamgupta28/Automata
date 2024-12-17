package dev.automata.automata.dto;

import lombok.*;

import java.util.Map;

@Getter
@Setter
@RequiredArgsConstructor
public class LiveEvent {
    private Map<String, Object> payload;
}
