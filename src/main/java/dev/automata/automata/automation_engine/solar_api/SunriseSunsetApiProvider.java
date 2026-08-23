package dev.automata.automata.automation_engine.solar_api;

import dev.automata.automata.service.RedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SunriseSunsetApiProvider implements ISolarTimeProvider {
    private final RedisService redisService;
    private final RestTemplate restTemplate; // injected as a @Bean, not `new`'d here
    @Value("${app.location.lat}")
    private String lat;
    @Value("${app.location.long}")
    private String lng;
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Override
    public Optional<LocalTime> getSunTime(String solarType, LocalDate date) {
        String cacheKey = "SUN_TIME:" + solarType + "-" + date;
        Object cached = redisService.get(cacheKey);
        if (cached != null) return Optional.of(LocalTime.parse(cached.toString()));

        SolarApiResult response = restTemplate.getForObject(
                "https://api.sunrise-sunset.org/json?lat=" + lat
                        + "&lng=" + lng + "&formatted=0", SolarApiResult.class);
        if (response == null)
            return Optional.empty();


        SolarApiResult.Results results = response.getResults();
        String ts = "sunrise".equalsIgnoreCase(solarType)
                ? results.getSunrise().toString() : results.getSunset().toString();
        if (ts == null) return Optional.empty();

        LocalTime result = ZonedDateTime.parse(ts).withZoneSameInstant(IST).toLocalTime();
        ZonedDateTime nowZ = ZonedDateTime.now(IST);
        long ttl = ChronoUnit.SECONDS.between(nowZ, nowZ.plusDays(1).truncatedTo(ChronoUnit.DAYS));
        redisService.setWithExpiry(cacheKey, result.toString(), ttl);
        return Optional.of(result);
    }
}
