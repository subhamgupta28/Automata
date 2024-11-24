package dev.automata.automata.dto;


import lombok.*;

import java.util.List;
import java.util.Map;


@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class ChartDataDto {
    public String label;
    public String unit;
    public String dataKey;
    public List<Object> data;
    public String deviceId;
    public String period; // weekly, monthly, yearly
    public String message;
    public List<String> timestamps;
    public List<String> attributes;
}
