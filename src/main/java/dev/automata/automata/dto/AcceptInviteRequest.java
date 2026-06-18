package dev.automata.automata.dto;

import lombok.Data;

/**
 * Body for POST /api/v1/invites/accept.
 */
@Data
public class AcceptInviteRequest {
    private String token;
}
