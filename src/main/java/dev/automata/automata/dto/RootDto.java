package dev.automata.automata.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RootDto {
    public List<ValueDto> values;
    public Long timestamp;
    public String date;
}
