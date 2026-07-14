package dev.automata.automata.modules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class SpotifyService {

    private static final String ACCOUNTS_BASE = "https://accounts.spotify.com";
    private static final String API_BASE = "https://api.spotify.com/v1";

    private final SpotifyProperties props;
    private final SpotifyTokenStore tokenStore;
    private final RestTemplate rest;
    private final ObjectMapper mapper;

    public SpotifyService(SpotifyProperties props, SpotifyTokenStore tokenStore) {
        this.props = props;
        this.tokenStore = tokenStore;
        this.rest = new RestTemplate();
        this.mapper = new ObjectMapper();
    }

    // ── OAuth ────────────────────────────────────────────────────────────────

    public String buildAuthUrl(String state) {
        return UriComponentsBuilder.fromUriString(ACCOUNTS_BASE + "/authorize")
                .queryParam("client_id", props.getClientId())
                .queryParam("response_type", "code")
                .queryParam("redirect_uri", props.getRedirectUri())
                .queryParam("scope", URLEncoder.encode(props.getScopes().trim(), StandardCharsets.UTF_8))
                .queryParam("state", state)
                .build(true)
                .toUriString();
    }

    public void exchangeCode(String code) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("code", code);
        body.add("redirect_uri", props.getRedirectUri());

        JsonNode resp = postToTokenEndpoint(body);
        tokenStore.store(
                resp.get("access_token").asText(),
                resp.path("refresh_token").asText(null),
                resp.get("expires_in").asLong(3600)
        );
    }

    public void refreshAccessToken() {
        if (!tokenStore.hasRefreshToken()) throw new IllegalStateException("No refresh token stored");

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("refresh_token", tokenStore.getRefreshToken());

        JsonNode resp = postToTokenEndpoint(body);
        tokenStore.store(
                resp.get("access_token").asText(),
                resp.path("refresh_token").asText(tokenStore.getRefreshToken()), // may not return new one
                resp.get("expires_in").asLong(3600)
        );
    }

    private JsonNode postToTokenEndpoint(MultiValueMap<String, String> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.set("Authorization", basicAuth());

        ResponseEntity<String> resp = rest.exchange(
                ACCOUNTS_BASE + "/api/token",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class
        );

        try {
            return mapper.readTree(resp.getBody());
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse token response", e);
        }
    }

    // ── Player API ───────────────────────────────────────────────────────────

    public JsonNode getCurrentlyPlaying() {
        return getApi("/me/player");
    }

    public JsonNode getDevices() {
        return getApi("/me/player/devices");
    }

    public void play(String deviceId) {
        String url = API_BASE + "/me/player/play" + (deviceId != null ? "?device_id=" + deviceId : "");
        putApi(url, null);
    }

    public void pause(String deviceId) {
        String url = API_BASE + "/me/player/pause" + (deviceId != null ? "?device_id=" + deviceId : "");
        putApi(url, null);
    }

    public void next(String deviceId) {
        String url = API_BASE + "/me/player/next" + (deviceId != null ? "?device_id=" + deviceId : "");
        postApi(url, null);
    }

    public void previous(String deviceId) {
        String url = API_BASE + "/me/player/previous" + (deviceId != null ? "?device_id=" + deviceId : "");
        postApi(url, null);
    }

    public void transferPlayback(String deviceId) {
        String body = "{\"device_ids\":[\"" + deviceId + "\"],\"play\":true}";
        putApi(API_BASE + "/me/player", body);
    }

    public void seek(int positionMs) {
        putApi(API_BASE + "/me/player/seek?position_ms=" + positionMs, null);
    }

    public void setVolume(int volumePercent) {
        putApi(API_BASE + "/me/player/volume?volume_percent=" + volumePercent, null);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private JsonNode getApi(String path) {
        ensureValidToken();
        try {
            ResponseEntity<String> resp = rest.exchange(
                    API_BASE + path,
                    HttpMethod.GET,
                    new HttpEntity<>(bearerHeaders()),
                    String.class
            );
            if (resp.getStatusCode() == HttpStatus.NO_CONTENT || resp.getBody() == null) return null;
            return mapper.readTree(resp.getBody());
        } catch (HttpClientErrorException.Unauthorized e) {
            refreshAccessToken();
            return getApi(path); // retry once
        } catch (Exception e) {
            throw new RuntimeException("Spotify GET failed: " + path, e);
        }
    }

    private void putApi(String url, String jsonBody) {
        ensureValidToken();
        try {
            HttpHeaders headers = bearerHeaders();
            if (jsonBody != null) headers.setContentType(MediaType.APPLICATION_JSON);
            rest.exchange(url, HttpMethod.PUT, new HttpEntity<>(jsonBody, headers), Void.class);
        } catch (HttpClientErrorException.Unauthorized e) {
            refreshAccessToken();
            putApi(url, jsonBody);
        }
    }

    private void postApi(String url, String jsonBody) {
        ensureValidToken();
        try {
            HttpHeaders headers = bearerHeaders();
            if (jsonBody != null) headers.setContentType(MediaType.APPLICATION_JSON);
            rest.exchange(url, HttpMethod.POST, new HttpEntity<>(jsonBody, headers), Void.class);
        } catch (HttpClientErrorException.Unauthorized e) {
            refreshAccessToken();
            postApi(url, jsonBody);
        }
    }

    private void ensureValidToken() {
        if (!tokenStore.hasValidToken()) {
            if (tokenStore.hasRefreshToken()) {
                refreshAccessToken();
            } else {
                throw new IllegalStateException("NOT_AUTHENTICATED");
            }
        }
    }

    private HttpHeaders bearerHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set("Authorization", "Bearer " + tokenStore.getAccessToken());
        return h;
    }

    private String basicAuth() {
        String credentials = props.getClientId() + ":" + props.getClientSecret();
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    public boolean isAuthenticated() {
        return tokenStore.hasValidToken() || tokenStore.hasRefreshToken();
    }
}
