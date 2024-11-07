package dev.automata.automata.dto;


import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class ChartDataAggregate {
    private String date;
    private List<Map<String, Object>> dataList;
}
