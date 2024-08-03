package dev.automata.automata.dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Reading {
    private String shuntvoltage;
    private String busvoltage;
    private String current_mA;
    private String power_mW;
    private String total_energy_mWh;
    private String loadvoltage;
    private String percent;
    private String energy;
}
