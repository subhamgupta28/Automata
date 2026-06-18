package dev.automata.automata.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "invites")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Invite {

    @Id
    private String token;            // UUID, used as the URL token
    private String homeId;
    private String invitedByUserId;
    private String invitedEmail;     // optional: lock to specific email
    private HomeRole roleToGrant;
    private Instant expiresAt;       // createdAt + 7 days
    private boolean used;

    @Indexed(expireAfter = "7d")
    private Instant ttl;             // MongoDB TTL index auto-deletes after expiry
}
