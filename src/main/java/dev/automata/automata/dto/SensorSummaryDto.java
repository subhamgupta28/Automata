package dev.automata.automata.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Rich analytics response for a single device with typed summaries for
 * energy, environment (AQI/CO2/temp/humidity), presence, and light sensors.
 */
@Data
@Builder
public class SensorSummaryDto {

    private String deviceId;
    private String deviceName;
    private String sensorType;   // "ENERGY", "ENV", "PRESENCE", "LIGHT", "GENERIC"

    // ── Time-series chart data (already aggregated per chosen granularity) ──
    private List<String> labels;          // x-axis labels
    private List<String> attributes;      // which keys appear in dataPoints
    private List<Map<String, Object>> dataPoints; // one entry per time bucket

    // ── Stat summary (last-window min/max/avg per attribute) ──
    private Map<String, AttributeStats> stats;

    // ── Derived / convenience fields ──
    private Integer airQualityScore;   // 0–100, null if not an env device
    private String airQualityLabel;   // "GOOD", "MODERATE", "POOR", etc.

    @Data
    @Builder
    public static class AttributeStats {
        private String key;
        private String unit;
        private Double current;
        private Double min;
        private Double max;
        private Double avg;
        private Double trend;   // % change vs prior window
    }
}