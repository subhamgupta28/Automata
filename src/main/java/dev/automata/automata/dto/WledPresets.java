package dev.automata.automata.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WledPresets {
    private String ip;
    private String mac;
    private String name;
    private List<Preset> presets;
}

