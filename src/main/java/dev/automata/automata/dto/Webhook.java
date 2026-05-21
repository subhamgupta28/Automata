package dev.automata.automata.dto;

import lombok.*;

@Getter
@Setter
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Webhook {
    private String event;
    private String device;
    private String action;
    private String value;
    private String state;
    private String timestamp;
}
//      {
//        "event": "smart_home_control",
//        "deviceId": "living_room_light",
//        "action": "ON",
//        "value": "80% brightness",
//        "state": "ON",
//        "timestamp": "2026-05-21T05:33:31.505Z"
//        }