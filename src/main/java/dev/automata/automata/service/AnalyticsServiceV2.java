package dev.automata.automata.service;

import dev.automata.automata.dto.AnalyticsQueryRequest;
import dev.automata.automata.dto.SensorSummaryDto;
import dev.automata.automata.dto.SensorSummaryDto.AttributeStats;
import dev.automata.automata.model.Attribute;
import dev.automata.automata.model.Device;
import dev.automata.automata.repository.AttributeRepository;
import dev.automata.automata.repository.DataRepository;
import dev.automata.automata.repository.DeviceChartsRepository;
import dev.automata.automata.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.*;

/**
 * V2 analytics service: time-travel, sensor-typed summaries, flexible granularity.
 */
@Service
@RequiredArgsConstructor
public class AnalyticsServiceV2 {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final DataRepository dataRepository;
    private final AttributeRepository attributeRepository;
    private final DeviceChartsRepository deviceChartsRepository;
    private final DeviceRepository deviceRepository;
    private final MongoTemplate mongoTemplate;

    // ──────────────────────────────────────────────────────────
    // Public entry point
    // ──────────────────────────────────────────────────────────

    public SensorSummaryDto query(String deviceId, AnalyticsQueryRequest req) {

        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Device not found"));

        List<Attribute> attrs = getVisibleAttributes(deviceId);
        if (attrs.isEmpty()) {
            return SensorSummaryDto.builder()
                    .deviceId(deviceId)
                    .deviceName(device.getName())
                    .sensorType(inferSensorType(device, attrs))
                    .labels(List.of())
                    .attributes(List.of())
                    .dataPoints(List.of())
                    .stats(Map.of())
                    .build();
        }

        Instant[] window = resolveWindow(req);
        Instant from = window[0];
        Instant to = window[1];

        String granularity = resolveGranularity(req, from, to);
        String sensorType = inferSensorType(device, attrs);

        List<Map<String, Object>> points = aggregate(deviceId, attrs, from, to, granularity);
        List<String> labels = points.stream()
                .map(p -> String.valueOf(p.get("dateDay")))
                .collect(Collectors.toList());

        Map<String, AttributeStats> stats = computeStats(deviceId, attrs, from, to);

        // Derived scores for env sensors
        Integer aqiScore = null;
        String aqiLabel = null;
        if ("ENV".equals(sensorType)) {
            aqiScore = computeAirQualityScore(stats);
            aqiLabel = airQualityLabel(aqiScore);
        }

        return SensorSummaryDto.builder()
                .deviceId(deviceId)
                .deviceName(device.getName())
                .sensorType(sensorType)
                .labels(labels)
                .attributes(attrs.stream().map(Attribute::getKey).collect(Collectors.toList()))
                .dataPoints(points)
                .stats(stats)
                .airQualityScore(aqiScore)
                .airQualityLabel(aqiLabel)
                .build();
    }

    // ──────────────────────────────────────────────────────────
    // Window / granularity resolution
    // ──────────────────────────────────────────────────────────

    private Instant[] resolveWindow(AnalyticsQueryRequest req) {
        Instant to = Instant.now();
        Instant from;

        if ("custom".equalsIgnoreCase(req.getRange())
                && req.getFrom() != null && req.getTo() != null) {
            from = Instant.parse(req.getFrom());
            to = Instant.parse(req.getTo());
            return new Instant[]{from, to};
        }

        from = switch (req.getRange().toLowerCase()) {
            case "hour" -> to.minus(1, ChronoUnit.HOURS);
            case "6h" -> to.minus(6, ChronoUnit.HOURS);
            case "day" -> to.minus(24, ChronoUnit.HOURS);
            case "week" -> to.minus(7, ChronoUnit.DAYS);
            case "month" -> to.minus(30, ChronoUnit.DAYS);
            case "3month" -> to.minus(90, ChronoUnit.DAYS);
            case "year" -> to.minus(365, ChronoUnit.DAYS);
            default -> to.minus(24, ChronoUnit.HOURS);
        };
        return new Instant[]{from, to};
    }

    private String resolveGranularity(AnalyticsQueryRequest req, Instant from, Instant to) {
        if (!"auto".equalsIgnoreCase(req.getGranularity()) && req.getGranularity() != null) {
            return req.getGranularity();
        }
        long hours = Duration.between(from, to).toHours();
        if (hours <= 2) return "5min";
        if (hours <= 12) return "30min";
        if (hours <= 48) return "1h";
        if (hours <= 336) return "6h";   // ≤14 days
        return "1d";
    }

    // ──────────────────────────────────────────────────────────
    // MongoDB aggregation pipeline
    // ──────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> aggregate(
            String deviceId,
            List<Attribute> attrs,
            Instant from,
            Instant to,
            String granularity) {

        MatchOperation match = match(
                Criteria.where("deviceId").is(deviceId)
                        .and("updateDate").gte(Date.from(from)).lte(Date.from(to))
        );

        // Bucket by granularity using $dateTrunc
        int binSize;
        String unit;
        String dateFormat;
        switch (granularity) {
            case "5min" -> {
                binSize = 5;
                unit = "minute";
                dateFormat = "%m-%d %H:%M";
            }
            case "30min" -> {
                binSize = 30;
                unit = "minute";
                dateFormat = "%m-%d %H:%M";
            }
            case "1h" -> {
                binSize = 1;
                unit = "hour";
                dateFormat = "%m-%d %H:00";
            }
            case "6h" -> {
                binSize = 6;
                unit = "hour";
                dateFormat = "%m-%d %H:00";
            }
            default -> {
                binSize = 1;
                unit = "day";
                dateFormat = "%Y-%m-%d";
            }
        }

        // Stage 1 – project numeric fields + dateTrunc slot
        ProjectionOperation project1 = project()
                .andExpression("{ $dateTrunc: { date: \"$updateDate\", unit: \"" + unit
                        + "\", binSize: " + binSize + ", timezone: \"Asia/Kolkata\" } }")
                .as("slot");

        for (Attribute attr : attrs) {
            project1 = project1
                    .andExpression("{ $convert: { input: \"$data." + attr.getKey()
                            + "\", to: \"double\", onError: null, onNull: null } }")
                    .as(attr.getKey());
        }

        // Stage 2 – group by slot, compute avg per attribute
        GroupOperation group = group("slot");
        for (Attribute attr : attrs) {
            group = group.avg(attr.getKey()).as(attr.getKey());
        }

        // Stage 3 – reformat slot → dateDay string, round values
        ProjectionOperation project2 = project()
                .andExpression("{ $dateToString: { format: \"" + dateFormat
                        + "\", date: \"$_id\", timezone: \"Asia/Kolkata\" } }")
                .as("dateDay");

        for (Attribute attr : attrs) {
            project2 = project2
                    .and(ArithmeticOperators.Round.roundValueOf(attr.getKey()).place(2))
                    .as(attr.getKey());
        }

        Aggregation agg = newAggregation(
                match,
                project1,
                group,
                project2,
                sort(Sort.by("dateDay"))
        );

        return mongoTemplate.aggregate(agg, "data", Object.class)
                .getMappedResults()
                .stream()
                .map(o -> (Map<String, Object>) o)
                .collect(Collectors.toList());
    }

    // ──────────────────────────────────────────────────────────
    // Stats computation (min/max/avg/trend per attribute)
    // ──────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, AttributeStats> computeStats(
            String deviceId,
            List<Attribute> attrs,
            Instant from,
            Instant to) {

        MatchOperation match = match(
                Criteria.where("deviceId").is(deviceId)
                        .and("updateDate").gte(Date.from(from)).lte(Date.from(to))
        );

        ProjectionOperation project = project();
        for (Attribute attr : attrs) {
            project = project.andExpression(
                            "{ $convert: { input: \"$data." + attr.getKey()
                                    + "\", to: \"double\", onError: null, onNull: null } }")
                    .as(attr.getKey());
        }

        GroupOperation group = group(); // single group = overall window
        for (Attribute attr : attrs) {
            group = group
                    .avg(attr.getKey()).as("avg_" + attr.getKey())
                    .min(attr.getKey()).as("min_" + attr.getKey())
                    .max(attr.getKey()).as("max_" + attr.getKey())
                    .last(attr.getKey()).as("last_" + attr.getKey());
        }

        Aggregation agg = newAggregation(match, project, group);
        List<Object> results = mongoTemplate.aggregate(agg, "data", Object.class).getMappedResults();

        Map<String, AttributeStats> statsMap = new LinkedHashMap<>();
        if (results.isEmpty()) return statsMap;

        Map<String, Object> row = (Map<String, Object>) results.get(0);
        Map<String, String> unitsMap = attrs.stream()
                .collect(Collectors.toMap(Attribute::getKey, a -> a.getUnits() == null ? "" : a.getUnits()));

        // Prior window for trend
        Duration windowLen = Duration.between(from, to);
        Instant priorFrom = from.minus(windowLen);
        Instant priorTo = from;
        Map<String, Double> priorAvgs = computePriorAvgs(deviceId, attrs, priorFrom, priorTo);

        for (Attribute attr : attrs) {
            String k = attr.getKey();
            Double avg = toDouble(row.get("avg_" + k));
            Double min = toDouble(row.get("min_" + k));
            Double max = toDouble(row.get("max_" + k));
            Double last = toDouble(row.get("last_" + k));
            Double priorAvg = priorAvgs.get(k);

            Double trend = null;
            if (avg != null && priorAvg != null && priorAvg != 0) {
                trend = ((avg - priorAvg) / Math.abs(priorAvg)) * 100.0;
                trend = Math.round(trend * 10.0) / 10.0;
            }

            statsMap.put(k, AttributeStats.builder()
                    .key(k)
                    .unit(unitsMap.getOrDefault(k, ""))
                    .current(last)
                    .min(min)
                    .max(max)
                    .avg(avg)
                    .trend(trend)
                    .build());
        }
        return statsMap;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Double> computePriorAvgs(String deviceId, List<Attribute> attrs,
                                                 Instant from, Instant to) {
        MatchOperation match = match(
                Criteria.where("deviceId").is(deviceId)
                        .and("updateDate").gte(Date.from(from)).lte(Date.from(to))
        );
        ProjectionOperation project = project();
        for (Attribute attr : attrs) {
            project = project.andExpression(
                            "{ $convert: { input: \"$data." + attr.getKey()
                                    + "\", to: \"double\", onError: null, onNull: null } }")
                    .as(attr.getKey());
        }
        GroupOperation group = group();
        for (Attribute attr : attrs) {
            group = group.avg(attr.getKey()).as("avg_" + attr.getKey());
        }
        Aggregation agg = newAggregation(match, project, group);
        List<Object> results = mongoTemplate.aggregate(agg, "data", Object.class).getMappedResults();
        if (results.isEmpty()) return Map.of();

        Map<String, Object> row = (Map<String, Object>) results.get(0);
        Map<String, Double> out = new LinkedHashMap<>();
        for (Attribute attr : attrs) {
            out.put(attr.getKey(), toDouble(row.get("avg_" + attr.getKey())));
        }
        return out;
    }

    // ──────────────────────────────────────────────────────────
    // Sensor type inference
    // ──────────────────────────────────────────────────────────

    private String inferSensorType(Device device, List<Attribute> attrs) {
        Set<String> keys = attrs.stream()
                .map(a -> a.getKey().toLowerCase())
                .collect(Collectors.toSet());

        // Energy / battery
        if (keys.stream().anyMatch(k -> k.contains("wh") || k.contains("energy")
                || k.contains("power") || k.contains("current") || k.contains("voltage")
                || k.contains("percent") || k.contains("battery"))) {
            return "ENERGY";
        }
        // Environment air quality
        if (keys.stream().anyMatch(k -> k.contains("aqi") || k.contains("pm")
                || k.contains("co2") || k.contains("co") || k.contains("tvoc")
                || k.contains("voc") || k.contains("temp") || k.contains("humid")
                || k.contains("pressure"))) {
            return "ENV";
        }
        // Presence / PIR / radar
        if (keys.stream().anyMatch(k -> k.contains("presence") || k.contains("motion")
                || k.contains("pir") || k.contains("occupancy"))) {
            return "PRESENCE";
        }
        // Light / lux / illuminance
        if (keys.stream().anyMatch(k -> k.contains("lux") || k.contains("light")
                || k.contains("illumin") || k.contains("uv"))) {
            return "LIGHT";
        }
        return "GENERIC";
    }

    // ──────────────────────────────────────────────────────────
    // Air quality score (0 = worst, 100 = best)
    // ──────────────────────────────────────────────────────────

    private Integer computeAirQualityScore(Map<String, AttributeStats> stats) {
        Double aqi = currentOf(stats, "aqi", "AQI", "air_quality");
        Double pm25 = currentOf(stats, "pm25", "pm2_5", "pm2.5", "particulate");
        Double co2 = currentOf(stats, "co2", "CO2", "carbon");
        Double tvoc = currentOf(stats, "tvoc", "voc", "TVOC");

        if (aqi == null && pm25 == null && co2 == null && tvoc == null) return null;

        double penalty = 0;
        int weights = 0;

        if (aqi != null) {
            penalty += Math.min(aqi / 300.0, 1) * 40;
            weights += 40;
        }
        if (pm25 != null) {
            penalty += Math.min(pm25 / 250.0, 1) * 30;
            weights += 30;
        }
        if (co2 != null) {
            penalty += Math.min(co2 / 2000.0, 1) * 20;
            weights += 20;
        }
        if (tvoc != null) {
            penalty += Math.min(tvoc / 1.0, 1) * 10;
            weights += 10;
        }

        if (weights == 0) return null;
        double scaled = (penalty / weights) * 100; // normalised 0-100 penalty
        return (int) Math.max(0, Math.min(100, Math.round(100 - scaled)));
    }

    private String airQualityLabel(Integer score) {
        if (score == null) return null;
        if (score >= 90) return "EXCELLENT";
        if (score >= 70) return "GOOD";
        if (score >= 50) return "MODERATE";
        if (score >= 30) return "POOR";
        return "HAZARDOUS";
    }

    // ──────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────

    private List<Attribute> getVisibleAttributes(String deviceId) {
        var charts = deviceChartsRepository.findByDeviceId(deviceId);
        var allAttrs = attributeRepository.findByDeviceId(deviceId);
        return allAttrs.stream()
                .filter(a -> charts.stream()
                        .anyMatch(c -> c.getAttributeKey().equals(a.getKey()) && c.isShowChart()))
                .collect(Collectors.toList());
    }

    private Double toDouble(Object val) {
        if (val == null) return null;
        try {
            return Double.parseDouble(val.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Double currentOf(Map<String, AttributeStats> stats, String... candidates) {
        for (String c : candidates) {
            // case-insensitive search
            for (var entry : stats.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(c) && entry.getValue().getCurrent() != null) {
                    return entry.getValue().getCurrent();
                }
            }
        }
        return null;
    }
}