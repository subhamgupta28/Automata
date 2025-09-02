package dev.automata.automata.model;

import lombok.*;
import org.springframework.data.annotation.Id;

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
}
