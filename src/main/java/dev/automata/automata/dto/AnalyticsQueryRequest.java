package dev.automata.automata.dto;

import lombok.Data;

@Data
public class AnalyticsQueryRequest {
    // Preset range: "hour", "day", "week", "month", "custom"
    private String range = "day";

    // For custom range (ISO 8601 strings from frontend)
    private String from;   // e.g. "2025-08-01T00:00:00Z"
    private String to;     // e.g. "2025-08-29T23:59:59Z"

    // Granularity override: "auto", "5min", "30min", "1h", "6h", "1d"
    private String granularity = "auto";
}