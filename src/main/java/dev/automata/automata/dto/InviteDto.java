package dev.automata.automata.dto;

import dev.automata.automata.model.HomeRole;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@RequiredArgsConstructor
@AllArgsConstructor
public class InviteDto {

    private String homeId;
    private String email;
    private HomeRole roleToGrant;
}
