package dev.automata.automata.model;


import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class DeviceActionState {

    @Id
    private String id;
    private String deviceId;
    private Map<String, Object> payload;
    private String deviceType;
    private String user;
    private Date timestamp;
    private Map<String, Object> deviceCurrentState;

}
