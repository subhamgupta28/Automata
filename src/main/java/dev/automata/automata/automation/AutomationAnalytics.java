package dev.automata.automata.automation;

import lombok.Builder;
import lombok.Data;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * LOW PRIORITY: DTO for automation analytics dashboard
 */
@Data
@Builder
public class AutomationAnalytics {
    // Basic info
    private String automationId;
    private String automationName;
    private int periodDays;

    // Execution statistics
    private long totalEvaluations;
    private long triggeredCount;
    private long restoredCount;
    private long skippedCount;
    private long notMetCount;

    // Performance metrics
    private double successRate; // Percentage
    private double averageConditionsPassed;

    // Temporal analysis
    private Map<String, Long> triggersByDay; // Date -> Count
    private List<String> mostCommonTriggerTimes; // "HH:00 (N times)"

    // Failure analysis
    private List<String> failureReasons; // Top 3 reasons with counts

    // Impact analysis
    private List<String> affectedDevices;
    private Date lastTriggered;
    private boolean isCurrentlyActive;
}
