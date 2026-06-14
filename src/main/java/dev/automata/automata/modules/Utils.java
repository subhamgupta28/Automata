package dev.automata.automata.modules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class Utils {

    @Value("${app.location.lat}")
    private String LOCATION_LAT;
    @Value("${app.location.long}")
    private String LOCATION_LONG;

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private static final String OPEN_METEO_URL = "https://api.open-meteo.com/v1/forecast";
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Fetch current outdoor weather from Open-Meteo (no API key required).
     *
     * @return WeatherResponse with temp, humidity, condition, windspeed, etc.
     */
    public WeatherResponse getCurrentWeather() throws Exception {
        URI uri = UriComponentsBuilder.fromHttpUrl(OPEN_METEO_URL)
                .queryParam("latitude", LOCATION_LAT)
                .queryParam("longitude", LOCATION_LONG)
                .queryParam("current", String.join(",",
                        "temperature_2m",
                        "relative_humidity_2m",
                        "apparent_temperature",
                        "weather_code",
                        "wind_speed_10m",
                        "precipitation",
                        "cloud_cover",
                        "is_day"
                ))
                .queryParam("hourly", String.join(",",
                        "weather_code", "precipitation_probability"
                ))
                .queryParam("forecast_days", 2)
                .queryParam("wind_speed_unit", "kmh")
                .queryParam("timezone", "auto")   // Open-Meteo infers TZ from lat/lon
                .build()
                .toUri();

        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET()
                .header("Accept", "application/json")
                .build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Open-Meteo error: HTTP " + response.statusCode());
        }

        JsonNode root = mapper.readTree(response.body());
        JsonNode current = root.path("current");

        double temp = current.path("temperature_2m").asDouble();
        int humidity = current.path("relative_humidity_2m").asInt();
        double feelsLike = current.path("apparent_temperature").asDouble();
        int weatherCode = current.path("weather_code").asInt();
        double windSpeed = current.path("wind_speed_10m").asDouble();
        double precip = current.path("precipitation").asDouble();
        int cloudCover = current.path("cloud_cover").asInt();
        boolean isDay = current.path("is_day").asInt() == 1;
        String timezone = root.path("timezone").asText();
        JsonNode hTimes = root.path("hourly").path("time");
        JsonNode hCodes = root.path("hourly").path("weather_code");
        JsonNode hPrecipP = root.path("hourly").path("precipitation_probability");
        String todayDate = hTimes.get(0).asText().substring(0, 10); // "2025-06-15"
        String todayAlert = buildHourlyAlert(todayDate, hTimes, hCodes, hPrecipP, timezone);
        System.err.println("TODAY " + todayAlert);
        System.err.println("TZ: " + timezone);
        System.err.println("First hourly time: " + hTimes.get(0).asText());
        System.err.println("Hourly count: " + hTimes.size());
        System.err.println("Current local hour: " + LocalDateTime.now(ZoneId.of(timezone)).getHour());
        return new WeatherResponse(
                temp,
                humidity,
                feelsLike,
                weatherCode,
                resolveConditionLabel(weatherCode, cloudCover, isDay),
                windSpeed,
                precip,
                cloudCover,
                isDay,
                timezone,
                todayAlert
        );
    }

    public List<ForecastDay> getForecast(int days) throws Exception {
        URI uri = UriComponentsBuilder.fromHttpUrl(OPEN_METEO_URL)
                .queryParam("latitude", LOCATION_LAT)
                .queryParam("longitude", LOCATION_LONG)
                .queryParam("daily", String.join(",",
                        "weather_code",
                        "temperature_2m_max",
                        "temperature_2m_min"
                ))
                .queryParam("forecast_days", days)
                .queryParam("timezone", "auto")
                .build().toUri();

        HttpRequest request = HttpRequest.newBuilder(uri)
                .GET().header("Accept", "application/json").build();

        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        JsonNode daily = mapper.readTree(response.body()).path("daily");
        JsonNode codes = daily.path("weather_code");
        JsonNode maxTemps = daily.path("temperature_2m_max");
        JsonNode minTemps = daily.path("temperature_2m_min");
        JsonNode dates = daily.path("time");

        List<ForecastDay> result = new ArrayList<>();
        for (int i = 1; i <= Math.min(days, codes.size() - 1); i++) { // skip today (index 0)
            result.add(new ForecastDay(
                    dates.get(i).asText(),
                    codes.get(i).asInt(),
                    maxTemps.get(i).asDouble(),
                    minTemps.get(i).asDouble(),
                    resolveConditionLabel(codes.get(i).asInt(), 50, true)
            ));
        }
        return result;
    }

    public record ForecastDay(
            String date,
            int weatherCode,
            double tempMax,
            double tempMin,
            String conditionLabel
    ) {
    }

    /**
     * WMO Weather Interpretation Codes → human-readable label.
     * Full table: https://open-meteo.com/en/docs#weathervariables
     */
    private String resolveConditionLabel(int code, int cloudCover, boolean isDay) {
        if (code == 0) return isDay ? "Clear sky" : "Clear night";
        if (code == 1) return "Mainly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Overcast";
        if (code == 45 || code == 48) return "Foggy";
        if (code >= 51 && code <= 55) return "Drizzle";
        if (code >= 61 && code <= 65) return "Rain";
        if (code >= 71 && code <= 75) return "Snow";
        if (code == 77) return "Snow grains";
        if (code >= 80 && code <= 82) return "Rain showers";
        if (code >= 85 && code <= 86) return "Snow showers";
        if (code >= 95 && code <= 99) return "Thunderstorm";
        // Fallback: derive from cloud cover
        if (cloudCover < 20) return isDay ? "Sunny" : "Clear";
        if (cloudCover < 60) return "Partly cloudy";
        return "Cloudy";
    }

    // ── Response DTO ─────────────────────────────────────────────────────────
    private String buildHourlyAlert(String date, JsonNode times, JsonNode codes,
                                    JsonNode precipProbs, String timezone) {
        ZoneId zone = ZoneId.of(timezone);
        LocalDateTime now = LocalDateTime.now(zone);
        LocalDateTime cutoff = now.plusHours(24);

        int worstSeverity = 0;
        String worstLabel = null;
        LocalDateTime worstTime = null;

        for (int h = 0; h < times.size(); h++) {
            String t = times.get(h).asText(); // "2026-06-14T19:00"
            LocalDateTime slotTime = LocalDateTime.parse(t); // ISO format, no zone needed

            if (!slotTime.isAfter(now)) continue;       // skip past/current hour
            if (!slotTime.isBefore(cutoff)) continue;   // skip beyond 24h window

            int wCode = codes.get(h).asInt();
            int precipP = precipProbs.get(h).asInt();

            int severity = 0;
            String label = null;

            if (wCode >= 95) {
                severity = 5;
                label = "Thunderstorm";
            } else if (wCode >= 80) {
                severity = 4;
                label = "Rain showers";
            } else if (wCode >= 71) {
                severity = 4;
                label = "Snow";
            } else if (wCode >= 61) {
                severity = 3;
                label = "Rain";
            } else if (wCode >= 51) {
                severity = 2;
                label = "Drizzle";
            } else if (precipP >= 60) {
                severity = 1;
                label = "Possible rain";
            }

            if (severity > worstSeverity) {
                worstSeverity = severity;
                worstLabel = label;
                worstTime = slotTime;
            }
        }

        if (worstLabel == null) return null;

        // Format: "6 PM" if today, "Mon 6 PM" if tomorrow
        boolean isTomorrow = worstTime.toLocalDate().isAfter(now.toLocalDate());
        int hour = worstTime.getHour();
        String amPm = hour >= 12 ? "PM" : "AM";
        int display = hour % 12 == 0 ? 12 : hour % 12;
        String timeStr = (isTomorrow ? "Tomorrow " : "~") + display + " " + amPm;

        return "Expect " + worstLabel.toLowerCase() + " " + timeStr;
    }

    public record WeatherResponse(
            double temperature,    // °C
            int humidity,       // %
            double feelsLike,      // °C
            int weatherCode,    // WMO code
            String conditionLabel, // human-readable
            double windSpeed,      // km/h
            double precipitation,  // mm
            int cloudCover,     // %
            boolean isDay,
            String timezone,        // e.g. "Asia/Kolkata"
            String todayAlert
    ) {
    }
}
