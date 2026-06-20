package dev.automata.automata.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import dev.automata.automata.model.HomeRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HomeDto {
    private String id;
    private String name;
    private String ownerId;
    private HomeRole myRole;
    private String timezone;
}