package dev.automata.automata.modules;

import com.fasterxml.jackson.databind.JsonNode;
import dev.automata.automata.dto.RegisterDevice;
import dev.automata.automata.model.Attribute;
import dev.automata.automata.model.Status;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spotify device handler — mirrors the Wled pattern so that the automation
 * engine can dispatch actions (play, pause, next, previous, volume, seek,
 * transfer) to a Spotify player the same way it does for any other device.
 */
@Slf4j
public class Spotify {

    private final SpotifyService spotifyService;

    /**
     * The device-id of the active Spotify Connect device (may be null = active device).
     */
    private String activeDeviceId;

    public Spotify(SpotifyService spotifyService, String deviceId) {
        this.activeDeviceId = deviceId;
        this.spotifyService = spotifyService;
    }
    // ── Action dispatcher ────────────────────────────────────────────────────

    /**
     * Central entry-point called by the automation engine.
     * <p>
     * Supported action keys:
     * <pre>
     *   play          – resume / start playback (optional: deviceId)
     *   pause         – pause playback
     *   next          – skip to next track
     *   previous      – go to previous track
     *   volume        – set volume 0-100  (value: int)
     *   seek          – seek to position  (value: int, milliseconds)
     *   transfer      – transfer playback (value: String deviceId)
     *   deviceId      – sets the target device for subsequent commands
     * </pre>
     *
     * @param input Map of action-key → value (same contract as {@link Wled#handleAction})
     * @return "success" on success, null on error
     */
    public String handleAction(Map<String, Object> input) {
        try {
            // Allow overriding the device inline
            if (input.containsKey("deviceId")) {
                activeDeviceId = input.get("deviceId").toString();
            }

            for (var entry : input.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                switch (key) {
                    case "play" -> {
                        String deviceId = value != null && !value.toString().isBlank()
                                ? value.toString() : activeDeviceId;
                        spotifyService.play(deviceId);
                        log.info("▶ Spotify play on device={}", deviceId);
                    }
                    case "pause" -> {
                        spotifyService.pause(activeDeviceId);
                        log.info("⏸ Spotify pause");
                    }
                    case "next" -> {
                        spotifyService.next(activeDeviceId);
                        log.info("⏭ Spotify next");
                    }
                    case "previous" -> {
                        spotifyService.previous(activeDeviceId);
                        log.info("⏮ Spotify previous");
                    }
                    case "volume" -> {
                        int vol = Integer.parseInt(value.toString());
                        spotifyService.setVolume(clamp(vol, 0, 100));
                        log.info("🔊 Spotify volume={}", vol);
                    }
                    case "seek" -> {
                        int posMs = Integer.parseInt(value.toString());
                        spotifyService.seek(posMs);
                        log.info("⏩ Spotify seek={}ms", posMs);
                    }
                    case "transfer" -> {
                        String targetDevice = value.toString();
                        spotifyService.transferPlayback(targetDevice);
                        activeDeviceId = targetDevice;
                        log.info("📲 Spotify transfer → device={}", targetDevice);
                    }
                    case "deviceId" -> { /* already handled above */ }
                    default -> log.warn("Spotify: unknown action key '{}'", key);
                }
            }

            return "success";

        } catch (Exception e) {
            log.error("Spotify handleAction error", e);
            return null;
        }
    }

    // ── State snapshot ───────────────────────────────────────────────────────

    /**
     * Polls the Spotify API and returns a flat attribute map compatible with
     * the automata state store (same shape as {@link Wled#convertToMap}).
     *
     * @param deviceId the automata device-id (stored as device_id in the map)
     * @return map of current player state, or empty map on error / nothing playing
     */
    public Map<String, Object> convertToMap(String deviceId) {
        Map<String, Object> state = new HashMap<>();
        state.put("device_id", deviceId);

        try {
            JsonNode player = spotifyService.getCurrentlyPlaying();
            if (player == null || player.isNull()) {
                state.put("isPlaying", false);
                return state;
            }

            state.put("isPlaying", player.path("is_playing").asBoolean(false));
            state.put("volume", player.path("device").path("volume_percent").asInt(50));
            state.put("progressMs", player.path("progress_ms").asLong(0));

            JsonNode item = player.path("item");
            if (!item.isMissingNode()) {
                state.put("trackName", item.path("name").asText(""));
                state.put("durationMs", item.path("duration_ms").asLong(0));

                JsonNode artists = item.path("artists");
                if (artists.isArray() && artists.size() > 0) {
                    state.put("artist", artists.get(0).path("name").asText(""));
                }

                JsonNode album = item.path("album");
                state.put("albumName", album.path("name").asText(""));

                JsonNode images = album.path("images");
                if (images.isArray() && images.size() > 0) {
                    state.put("albumArt", images.get(0).path("url").asText(""));
                }
            }

            JsonNode device = player.path("device");
            if (!device.isMissingNode()) {
                String spotifyDeviceId = device.path("id").asText(null);
                state.put("spotifyDeviceId", spotifyDeviceId);
                state.put("spotifyDeviceName", device.path("name").asText(""));
                if (spotifyDeviceId != null && activeDeviceId == null) {
                    activeDeviceId = spotifyDeviceId; // auto-sync active device
                }
            }

        } catch (Exception e) {
            log.error("Spotify convertToMap error", e);
        }

        return state;
    }

    // ── Device registration ──────────────────────────────────────────────────

    /**
     * Returns the {@link RegisterDevice} descriptor for a Spotify virtual device,
     * including all ACTION attributes that automations can target.
     */
    public RegisterDevice newDevice() {
        return RegisterDevice.builder()
                .deviceId("6a3155334266e550bf16d4e1")
                .name("Spotify")
                .sleep(false)
                .reboot(false)
                .host("spotify")
                .macAddr("VIRTUAL-0.33359636164196305")
                .accessUrl("")
                .type("MEDIA")
                .status(Status.ONLINE)
                .updateInterval(18000L)
                .attributes(List.of(

                        // ── Playback controls ──────────────────────────────
                        Attribute.builder()
                                .key("play")
                                .displayName("Play")
                                .type("ACTION|OUT")
                                .units("")
                                .extras(new HashMap<>())
                                .visible(true)
                                .build(),

                        Attribute.builder()
                                .key("pause")
                                .displayName("Pause")
                                .type("ACTION|OUT")
                                .units("")
                                .extras(new HashMap<>())
                                .visible(true)
                                .build(),

                        Attribute.builder()
                                .key("next")
                                .displayName("Next Track")
                                .type("ACTION|OUT")
                                .units("")
                                .extras(new HashMap<>())
                                .visible(true)
                                .build(),

                        Attribute.builder()
                                .key("previous")
                                .displayName("Previous Track")
                                .type("ACTION|OUT")
                                .units("")
                                .extras(new HashMap<>())
                                .visible(true)
                                .build(),

                        // ── Automation-friendly toggle ─────────────────────
                        Attribute.builder()
                                .key("isPlaying")
                                .displayName("Playing")
                                .type("ACTION|SWITCH")
                                .units("")
                                .extras(new HashMap<>())
                                .visible(true)
                                .build(),

                        // ── Volume slider ──────────────────────────────────
                        Attribute.builder()
                                .key("volume")
                                .displayName("Volume")
                                .type("ACTION|SLIDER")
                                .units("%")
                                .extras(Map.of("min", 0, "max", 100))
                                .visible(true)
                                .build(),

                        // ── Seek slider ────────────────────────────────────
                        Attribute.builder()
                                .key("seek")
                                .displayName("Seek Position")
                                .type("ACTION|SLIDER")
                                .units("ms")
                                .extras(Map.of("min", 0, "max", 600000))
                                .visible(false)        // internal / advanced
                                .build(),

                        // ── Read-only data attributes ──────────────────────
                        Attribute.builder()
                                .key("trackName")
                                .displayName("Track")
                                .type("DATA|TEXT")
                                .units("")
                                .extras(new HashMap<>())
                                .visible(true)
                                .build(),

                        Attribute.builder()
                                .key("artist")
                                .displayName("Artist")
                                .type("DATA|TEXT")
                                .units("")
                                .extras(new HashMap<>())
                                .visible(true)
                                .build(),

                        Attribute.builder()
                                .key("albumName")
                                .displayName("Album")
                                .type("DATA|TEXT")
                                .units("")
                                .extras(new HashMap<>())
                                .visible(true)
                                .build(),

                        Attribute.builder()
                                .key("albumArt")
                                .displayName("Album Art")
                                .type("DATA|IMAGE")
                                .units("")
                                .extras(new HashMap<>())
                                .visible(true)
                                .build(),

                        Attribute.builder()
                                .key("progressMs")
                                .displayName("Progress")
                                .type("DATA|AUX")
                                .units("ms")
                                .extras(new HashMap<>())
                                .visible(false)
                                .build(),

                        Attribute.builder()
                                .key("durationMs")
                                .displayName("Duration")
                                .type("DATA|AUX")
                                .units("ms")
                                .extras(new HashMap<>())
                                .visible(false)
                                .build(),

                        // ── Device transfer ────────────────────────────────
                        Attribute.builder()
                                .key("transfer")
                                .displayName("Transfer Playback")
                                .type("ACTION|OUT")
                                .units("")
                                .extras(new HashMap<>())
                                .visible(true)
                                .build(),

                        Attribute.builder()
                                .key("spotifyDeviceName")
                                .displayName("Active Device")
                                .type("DATA|TEXT")
                                .units("")
                                .extras(new HashMap<>())
                                .visible(true)
                                .build(),

                        // ── Housekeeping ───────────────────────────────────
                        Attribute.builder()
                                .key("last_seen")
                                .displayName("Last Seen")
                                .type("DATA|AUX")
                                .units("time")
                                .extras(new HashMap<>())
                                .visible(true)
                                .build()
                ))
                .build();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public boolean isAuthenticated() {
        return spotifyService.isAuthenticated();
    }

    public void setActiveDeviceId(String deviceId) {
        this.activeDeviceId = deviceId;
    }

    public String getActiveDeviceId() {
        return activeDeviceId;
    }
}