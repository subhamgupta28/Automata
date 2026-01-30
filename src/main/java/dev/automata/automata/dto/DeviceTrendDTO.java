package dev.automata.automata.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DeviceTrendDTO {

    private String deviceId;
    private Double avgTemp;
    private Double avgHumidity;
    private Double avgAqi;
    private Double avgPm25;
    private Double avgCo2;
    private String aqiTrend;
}
