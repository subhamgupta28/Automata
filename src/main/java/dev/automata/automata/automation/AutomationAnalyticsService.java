package dev.automata.automata.automation;


import dev.automata.automata.model.Automation;
import dev.automata.automata.model.AutomationLog;
import dev.automata.automata.repository.AutomationLogRepository;
import dev.automata.automata.repository.AutomationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * LOW PRIORITY: Automation Analytics Dashboard Service
 * Provides insights and metrics about automation performance
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AutomationAnalyticsService {

    private final AutomationRepository automationRepository;
    private final AutomationLogRepository automationLogRepository;

    /**
     * Get comprehensive analytics for an automation
     */
    public AutomationAnalytics getAutomationAnalytics(String automationId, int daysBack) {
        var automation = automationRepository.findById(automationId).orElse(null);
        if (automation == null) {
            return null;
        }

        Date cutoffDate = Date.from(Instant.now().minus(Duration.ofDays(daysBack)));

        // Fetch logs for the period
        List<AutomationLog> logs = automationLogRepository
                .findByAutomationIdAndTimestampAfter(automationId, cutoffDate);

        return AutomationAnalytics.builder()
                .automationId(automationId)
                .automationName(automation.getName())
                .periodDays(daysBack)
                .totalEvaluations(logs.size())
                .triggeredCount(countByStatus(logs, AutomationLog.LogStatus.TRIGGERED))
                .restoredCount(countByStatus(logs, AutomationLog.LogStatus.RESTORED))
                .skippedCount(countByStatus(logs, AutomationLog.LogStatus.SKIPPED))
                .notMetCount(countByStatus(logs, AutomationLog.LogStatus.NOT_MET))
                .successRate(calculateSuccessRate(logs))
                .averageConditionsPassed(calculateAverageConditionsPassed(logs))
                .triggersByDay(groupTriggersByDay(logs))
                .mostCommonTriggerTimes(getMostCommonTriggerTimes(logs))
                .failureReasons(getTopFailureReasons(logs))
                .affectedDevices(getAffectedDevices(automation))
                .lastTriggered(getLastTriggeredTime(logs))
                .isCurrentlyActive(isCurrentlyActive(logs))
                .build();
    }

    /**
     * Get analytics for all automations (overview)
     */
    public List<AutomationAnalytics> getAllAutomationAnalytics(int daysBack) {
        return automationRepository.findAll().stream()
                .map(a -> getAutomationAnalytics(a.getId(), daysBack))
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingLong(AutomationAnalytics::getTriggeredCount).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Get top performing automations by trigger count
     */
    public List<AutomationAnalytics> getTopPerformingAutomations(int limit, int daysBack) {
        return getAllAutomationAnalytics(daysBack).stream()
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Get automations with low success rate (potential issues)
     */
    public List<AutomationAnalytics> getProblematicAutomations(double successThreshold, int daysBack) {
        return getAllAutomationAnalytics(daysBack).stream()
                .filter(a -> a.getSuccessRate() < successThreshold)
                .filter(a -> a.getTotalEvaluations() > 10) // Only consider if evaluated enough times
                .sorted(Comparator.comparingDouble(AutomationAnalytics::getSuccessRate))
                .collect(Collectors.toList());
    }

    /**
     * Get automation execution timeline
     */
    public List<Map<String, Object>> getExecutionTimeline(String automationId, int hours) {
        Date cutoffDate = Date.from(Instant.now().minus(Duration.ofHours(hours)));

        List<AutomationLog> logs = automationLogRepository
                .findByAutomationIdAndTimestampAfter(automationId, cutoffDate);

        return logs.stream()
                .sorted(Comparator.comparing(AutomationLog::getTimestamp))
                .map(log -> {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("timestamp", log.getTimestamp());
                    entry.put("status", log.getStatus().toString());
                    entry.put("reason", log.getReason());
                    entry.put("conditionsPassed", countPassedConditions(log));
                    return entry;
                })
                .collect(Collectors.toList());
    }

    /**
     * Get device impact analysis - which devices are most affected by automations
     */
    public Map<String, Long> getDeviceImpactAnalysis(int daysBack) {
        List<Automation> automations = automationRepository.findAll();
        Map<String, Long> deviceImpact = new HashMap<>();

        for (Automation automation : automations) {
            Date cutoffDate = Date.from(Instant.now().minus(Duration.ofDays(daysBack)));
            long triggerCount = automationLogRepository
                    .countByAutomationIdAndStatusAndTimestampAfter(
                            automation.getId(),
                            AutomationLog.LogStatus.TRIGGERED,
                            cutoffDate
                    );

            for (Automation.Action action : automation.getActions()) {
                String deviceId = action.getDeviceId();
                if (deviceId != null && !deviceId.isEmpty()) {
                    deviceImpact.merge(deviceId, triggerCount, Long::sum);
                }
            }
        }

        return deviceImpact.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    /**
     * Get hourly distribution of triggers (when automations are most active)
     */
    public Map<Integer, Long> getHourlyTriggerDistribution(int daysBack) {
        Date cutoffDate = Date.from(Instant.now().minus(Duration.ofDays(daysBack)));

        List<AutomationLog> logs = automationLogRepository
                .findByStatusAndTimestampAfter(AutomationLog.LogStatus.TRIGGERED, cutoffDate);

        Map<Integer, Long> hourlyDistribution = new HashMap<>();
        ZoneId zone = ZoneId.of("Asia/Kolkata");

        for (AutomationLog log : logs) {
            int hour = log.getTimestamp().toInstant()
                    .atZone(zone)
                    .getHour();
            hourlyDistribution.merge(hour, 1L, Long::sum);
        }

        return hourlyDistribution.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    // Helper methods

    private long countByStatus(List<AutomationLog> logs, AutomationLog.LogStatus status) {
        return logs.stream()
                .filter(log -> log.getStatus() == status)
                .count();
    }

    private double calculateSuccessRate(List<AutomationLog> logs) {
        long total = logs.size();
        if (total == 0) return 0.0;

        long successful = countByStatus(logs, AutomationLog.LogStatus.TRIGGERED) +
                countByStatus(logs, AutomationLog.LogStatus.RESTORED);

        return (successful * 100.0) / total;
    }

    private double calculateAverageConditionsPassed(List<AutomationLog> logs) {
        if (logs.isEmpty()) return 0.0;

        double totalPassed = logs.stream()
                .mapToLong(this::countPassedConditions)
                .sum();

        return totalPassed / logs.size();
    }

    private long countPassedConditions(AutomationLog log) {
        if (log.getConditionResults() == null) return 0;

        return log.getConditionResults().stream()
                .filter(AutomationLog.ConditionResult::isPassed)
                .count();
    }

    private Map<String, Long> groupTriggersByDay(List<AutomationLog> logs) {
        ZoneId zone = ZoneId.of("Asia/Kolkata");

        return logs.stream()
                .filter(log -> log.getStatus() == AutomationLog.LogStatus.TRIGGERED)
                .collect(Collectors.groupingBy(
                        log -> {
                            LocalDate date = log.getTimestamp().toInstant()
                                    .atZone(zone)
                                    .toLocalDate();
                            return date.toString();
                        },
                        LinkedHashMap::new,
                        Collectors.counting()
                ));
    }

    private List<String> getMostCommonTriggerTimes(List<AutomationLog> logs) {
        ZoneId zone = ZoneId.of("Asia/Kolkata");

        Map<String, Long> timeFrequency = logs.stream()
                .filter(log -> log.getStatus() == AutomationLog.LogStatus.TRIGGERED)
                .collect(Collectors.groupingBy(
                        log -> {
                            int hour = log.getTimestamp().toInstant()
                                    .atZone(zone)
                                    .getHour();
                            return String.format("%02d:00", hour);
                        },
                        Collectors.counting()
                ));

        return timeFrequency.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> e.getKey() + " (" + e.getValue() + " times)")
                .collect(Collectors.toList());
    }

    private List<String> getTopFailureReasons(List<AutomationLog> logs) {
        return logs.stream()
                .filter(log -> log.getStatus() == AutomationLog.LogStatus.NOT_MET)
                .collect(Collectors.groupingBy(
                        AutomationLog::getReason,
                        Collectors.counting()
                ))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(3)
                .map(e -> e.getKey() + " (" + e.getValue() + " times)")
                .collect(Collectors.toList());
    }

    private List<String> getAffectedDevices(Automation automation) {
        return automation.getActions().stream()
                .map(Automation.Action::getDeviceId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
    }

    private Date getLastTriggeredTime(List<AutomationLog> logs) {
        return logs.stream()
                .filter(log -> log.getStatus() == AutomationLog.LogStatus.TRIGGERED)
                .max(Comparator.comparing(AutomationLog::getTimestamp))
                .map(AutomationLog::getTimestamp)
                .orElse(null);
    }

    private boolean isCurrentlyActive(List<AutomationLog> logs) {
        return logs.stream()
                .max(Comparator.comparing(AutomationLog::getTimestamp))
                .map(log -> log.getStatus() == AutomationLog.LogStatus.TRIGGERED)
                .orElse(false);
    }
}
