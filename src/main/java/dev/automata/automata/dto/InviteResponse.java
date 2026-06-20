package dev.automata.automata.dto;

import dev.automata.automata.model.HomeRole;
import dev.automata.automata.model.Invite;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Response returned when an invite is created. Exposes the token and a
 * shareable link, but not internal fields like the TTL marker.
 */
@Data
@Builder
public class InviteResponse {
    private String token;
    private String homeId;
    private String invitedEmail;
    private HomeRole roleToGrant;
    private Instant expiresAt;
    private String inviteLink;

    public static InviteResponse from(Invite invite, String baseUrl) {
        String link = baseUrl == null || baseUrl.isBlank()
                ? "/join?token=" + invite.getToken()
                : baseUrl.replaceAll("/+$", "") + "/join?token=" + invite.getToken();
        return InviteResponse.builder()
                .token(invite.getToken())
                .homeId(invite.getHomeId())
                .invitedEmail(invite.getInvitedEmail())
                .roleToGrant(invite.getRoleToGrant())
                .expiresAt(invite.getExpiresAt())
                .inviteLink(link)
                .build();
    }
}
