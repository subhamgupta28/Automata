package dev.automata.automata.dto;

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
    private String deviceSecret;
    private String homeId;
    private String category;
}
