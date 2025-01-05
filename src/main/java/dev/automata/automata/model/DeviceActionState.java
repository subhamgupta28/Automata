package dev.automata.automata.model;


import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class DeviceActionState {

    @Id
    private String deviceId;
    private Map<String, Object> payload;
    private String deviceType;
}
