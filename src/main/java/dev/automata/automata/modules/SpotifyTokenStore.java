package dev.automata.automata.modules;


import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Simple in-memory token store. For multi-user apps, key this by userId/session.
 * Replace with Redis or DB persistence as needed.
 */
@Component
public class SpotifyTokenStore {

    private String accessToken;
    private String refreshToken;
    private Instant expiresAt;

    public boolean hasValidToken() {
        return accessToken != null && Instant.now().isBefore(expiresAt != null ? expiresAt : Instant.MIN);
    }

    public void store(String accessToken, String refreshToken, long expiresInSeconds) {
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresAt = Instant.now().plusSeconds(expiresInSeconds - 30); // 30s buffer
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public boolean hasRefreshToken() {
        return refreshToken != null;
    }
}
