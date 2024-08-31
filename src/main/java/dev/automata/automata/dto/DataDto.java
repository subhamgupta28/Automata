package dev.automata.automata.dto;

import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DataDto {
    public String deviceId;
    public List<RootDto> values;
    public Integer pageSize;
    public Integer pageNo;
}
