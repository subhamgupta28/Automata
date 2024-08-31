package dev.automata.automata.dto;


import lombok.*;

@Getter
@Setter
@Builder
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ValueDto {
    public String displayName;
    public String key;
    public String units;
    public String value;
}
