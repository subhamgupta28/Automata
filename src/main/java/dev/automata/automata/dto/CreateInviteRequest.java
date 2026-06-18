package dev.automata.automata.dto;

import dev.automata.automata.model.HomeRole;
import lombok.Data;

/**
 * Body for POST /api/v1/homes/{homeId}/invites.
 * email is optional — when present the invite is locked to that address.
 */
@Data
public class CreateInviteRequest {
    private String email;       // optional
    private HomeRole role;      // role to grant on acceptance
}
