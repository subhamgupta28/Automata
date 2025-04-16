package dev.automata.automata.dto;


import dev.automata.automata.model.Attribute;
import dev.automata.automata.model.Status;
import lombok.*;
import org.w3c.dom.Attr;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class RegisterDevice {
    private String deviceId;
    private String name;
    private String type;
    private String host;
    private Long updateInterval;
    private Status status;
    private Boolean reboot;
    private Boolean sleep;
    private String accessUrl;
    private String macAddr;
    private List<Attribute> attributes;
}
