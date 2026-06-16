package dev.automata.automata.modules;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/spotify")
public class SpotifyController {

    private final SpotifyService spotifyService;

    public SpotifyController(SpotifyService spotifyService) {
        this.spotifyService = spotifyService;
    }

    /**
     * Check whether the backend already holds a valid token
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Boolean>> status() {
        return ResponseEntity.ok(Map.of("authenticated", spotifyService.isAuthenticated()));
    }

    /**
     * Redirect the user's browser to Spotify's auth page
     */
    @GetMapping("/login")
    public ResponseEntity<Void> login() {
        String state = UUID.randomUUID().toString();
        String authUrl = spotifyService.buildAuthUrl(state);
        return ResponseEntity.status(302).location(URI.create(authUrl)).build();
    }

    /**
     * Spotify redirects here after the user approves
     */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam String code,
            @RequestParam(required = false) String error
    ) {
        if (error != null) {
            // Redirect back to frontend with error flag
            return ResponseEntity.status(302)
                    .location(URI.create("http://localhost:5173?spotify_error=" + error))
                    .build();
        }
        spotifyService.exchangeCode(code);
        // Redirect back to the frontend page that opened the login
        return ResponseEntity.status(302)
                .location(URI.create("http://localhost:5173?spotify_connected=true"))
                .build();
    }

    /**
     * Currently playing track + player state
     */
    @GetMapping("/player")
    public ResponseEntity<JsonNode> player() {
        JsonNode state = spotifyService.getCurrentlyPlaying();
        if (state == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(state);
    }

    /**
     * List available devices
     */
    @GetMapping("/devices")
    public ResponseEntity<JsonNode> devices() {
        return ResponseEntity.ok(spotifyService.getDevices());
    }

    /**
     * Play / Resume
     */
    @PutMapping("/play")
    public ResponseEntity<Void> play(@RequestParam(required = false) String deviceId) {
        spotifyService.play(deviceId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Pause
     */
    @PutMapping("/pause")
    public ResponseEntity<Void> pause(@RequestParam(required = false) String deviceId) {
        spotifyService.pause(deviceId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Skip to next track
     */
    @PostMapping("/next")
    public ResponseEntity<Void> next(@RequestParam(required = false) String deviceId) {
        spotifyService.next(deviceId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Go to previous track
     */
    @PostMapping("/previous")
    public ResponseEntity<Void> previous(@RequestParam(required = false) String deviceId) {
        spotifyService.previous(deviceId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Transfer playback to a different device
     */
    @PutMapping("/transfer")
    public ResponseEntity<Void> transfer(@RequestParam String deviceId) {
        spotifyService.transferPlayback(deviceId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Seek to position in ms
     */
    @PutMapping("/seek")
    public ResponseEntity<Void> seek(@RequestParam int positionMs) {
        spotifyService.seek(positionMs);
        return ResponseEntity.noContent().build();
    }

    /**
     * Set volume 0–100
     */
    @PutMapping("/volume")
    public ResponseEntity<Void> volume(@RequestParam int percent) {
        spotifyService.setVolume(percent);
        return ResponseEntity.noContent().build();
    }
}