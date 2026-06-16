package dev.automata.automata.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Document("spotify_tokens")
public class SpotifyToken {

    @Id
    private String id;
    private String userId;
    private String accessToken;
    private String refreshToken;
    private Instant expiresAt;
}
