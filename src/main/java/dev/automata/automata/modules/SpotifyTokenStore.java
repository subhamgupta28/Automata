package dev.automata.automata.modules;

import dev.automata.automata.model.SpotifyToken;
import dev.automata.automata.repository.SpotifyTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class SpotifyTokenStore {

    private static final String TOKEN_ID = "default";

    private final SpotifyTokenRepository repository;

    public String getAccessToken() {
        return getToken().getAccessToken();
    }

    public String getRefreshToken() {
        return getToken().getRefreshToken();
    }

    public Instant getExpiresAt() {
        return getToken().getExpiresAt();
    }

    public boolean hasValidToken() {
        SpotifyToken token = getToken();

        return token.getAccessToken() != null
                && token.getExpiresAt() != null
                && Instant.now().isBefore(token.getExpiresAt());
    }

    public boolean hasRefreshToken() {
        return getToken().getRefreshToken() != null;
    }

    public void store(String accessToken, String refreshToken, long expiresInSeconds) {

        SpotifyToken token = SpotifyToken.builder()
                .id(TOKEN_ID)
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresAt(Instant.now().plusSeconds(expiresInSeconds - 30))
                .build();

        repository.save(token);
    }

    private SpotifyToken getToken() {
        return repository.findById(TOKEN_ID)
                .orElseGet(() -> {
                    SpotifyToken token = SpotifyToken.builder()
                            .id(TOKEN_ID)
                            .build();

                    return repository.save(token);
                });
    }
}