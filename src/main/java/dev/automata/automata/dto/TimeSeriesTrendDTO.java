package dev.automata.automata.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TimeSeriesTrendDTO {

    private String timeBucket;
    private Double avgAqi;
    private Double avgPm25;
    private Double avgTemp;
    private Double avgHumidity;
}
