package dev.automata.automata.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AutomationAnalyticsDto {
    private String automationId;
    private String automationName;
    private String lastTriggered;
    private Long avgEvalDurationMs;
    private Long avgActionFireDelayMs;
    private long undelivered;
    private long slowEvals;
    private long errors;
    private long totalTriggers;
}