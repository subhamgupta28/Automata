package dev.automata.automata.model;

import lombok.*;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class TriggerSource {
    private String deviceId;
    private List<String> keys;    // which keys from this device wake the automation
    private String role;          // "primary" | "secondary"
}
