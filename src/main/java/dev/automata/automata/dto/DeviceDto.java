package dev.automata.automata.dto;

import dev.automata.automata.model.Status;
import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class DeviceDto {
    private String id;
    private String name;
    private String type;
    private String host;
    private Long updateInterval;
    private String accessUrl;
    private String macAddr;
}
