package dev.automata.automata.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AutomationAnalyticsSummaryDto {
    private int total;
    private int healthy;
    private int warnings;
    private int errors;
    private long totalUndelivered;
    private long totalSlowEvals;
}
