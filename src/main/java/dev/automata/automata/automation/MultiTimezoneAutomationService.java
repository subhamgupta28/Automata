package dev.automata.automata.automation;

import dev.automata.automata.model.Automation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * LOW PRIORITY: Multi-timezone support for automations
 * Allows different automations to run in different timezones
 */
@Slf4j
@Service
public class MultiTimezoneAutomationService {

    private static final String DEFAULT_TIMEZONE = "Asia/Kolkata";

    // Common timezone mappings
    private static final Map<String, String> TIMEZONE_ALIASES = Map.ofEntries(
            Map.entry("IST", "Asia/Kolkata"),
            Map.entry("PST", "America/Los_Angeles"),
            Map.entry("EST", "America/New_York"),
            Map.entry("CST", "America/Chicago"),
            Map.entry("MST", "America/Denver"),
            Map.entry("GMT", "GMT"),
            Map.entry("UTC", "UTC"),
            Map.entry("JST", "Asia/Tokyo"),
            Map.entry("AEST", "Australia/Sydney"),
            Map.entry("CET", "Europe/Paris"),
            Map.entry("BST", "Europe/London")
    );

    /**
     * Check if current time matches condition in specified timezone
     */
    public boolean isCurrentTimeInTimezone(
            Automation.Condition condition,
            String timezoneId) {

        ZoneId zone = resolveTimezone(timezoneId);
        ZonedDateTime nowZdt = ZonedDateTime.now(zone);
        LocalTime current = nowZdt.toLocalTime();

        log.debug("Checking time in timezone: {} - Current time: {}", zone, current);

        // Check day of week if specified
        if (condition.getDays() != null && !condition.getDays().isEmpty()) {
            String today = nowZdt.getDayOfWeek()
                    .getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)
                    .substring(0, 3);
            today = today.substring(0, 1).toUpperCase() + today.substring(1).toLowerCase();

            if (!condition.getDays().contains(today)) {
                log.debug("Day {} not in schedule days: {}", today, condition.getDays());
                return false;
            }
        }

        String scheduleType = condition.getScheduleType();

        if ("range".equals(scheduleType)) {
            LocalTime from = parseTime(condition.getFromTime());
            LocalTime to = parseTime(condition.getToTime());

            if (from == null || to == null) {
                log.error("Invalid time range: from={}, to={}",
                        condition.getFromTime(), condition.getToTime());
                return false;
            }

            // Handle overnight ranges
            boolean inRange;
            if (from.isBefore(to)) {
                inRange = !current.isBefore(from) && !current.isAfter(to);
            } else {
                inRange = !current.isBefore(from) || !current.isAfter(to);
            }

            log.debug("Time range check: {} in [{}, {}] = {}", current, from, to, inRange);
            return inRange;
        }

        // "at" schedule type - exact time match within 1 minute
        LocalTime target = parseTime(condition.getTime());
        if (target == null) {
            log.error("Invalid target time: {}", condition.getTime());
            return false;
        }

        long minutesDiff = Math.abs(ChronoUnit.MINUTES.between(target, current));
        boolean matches = minutesDiff <= 1;

        log.debug("Exact time check: {} vs {} (diff: {} min) = {}",
                current, target, minutesDiff, matches);

        return matches;
    }

    /**
     * Convert time from one timezone to another
     */
    public ZonedDateTime convertTime(ZonedDateTime time, String fromZone, String toZone) {
        ZoneId from = resolveTimezone(fromZone);
        ZoneId to = resolveTimezone(toZone);

        return time.withZoneSameInstant(to);
    }

    /**
     * Get current time in specified timezone
     */
    public ZonedDateTime getCurrentTimeInZone(String timezoneId) {
        ZoneId zone = resolveTimezone(timezoneId);
        return ZonedDateTime.now(zone);
    }

    /**
     * Get all available timezone IDs
     */
    public Set<String> getAvailableTimezones() {
        return ZoneId.getAvailableZoneIds();
    }

    /**
     * Get common timezone options for UI
     */
    public Map<String, String> getCommonTimezones() {
        return new LinkedHashMap<>(TIMEZONE_ALIASES);
    }

    /**
     * Resolve timezone ID from string (handles aliases)
     */
    private ZoneId resolveTimezone(String timezoneId) {
        if (timezoneId == null || timezoneId.isEmpty()) {
            return ZoneId.of(DEFAULT_TIMEZONE);
        }

        // Check if it's an alias
        String resolved = TIMEZONE_ALIASES.getOrDefault(
                timezoneId.toUpperCase(),
                timezoneId
        );

        try {
            return ZoneId.of(resolved);
        } catch (Exception e) {
            log.warn("Invalid timezone '{}', falling back to default: {}",
                    timezoneId, DEFAULT_TIMEZONE);
            return ZoneId.of(DEFAULT_TIMEZONE);
        }
    }

    /**
     * Parse time string (supports 24h and 12h formats)
     */
    private LocalTime parseTime(String timeText) {
        if (timeText == null || timeText.isBlank()) return null;

        try {
            return LocalTime.parse(timeText.trim(), DateTimeFormatter.ofPattern("HH:mm:ss"));
        } catch (Exception e1) {
            try {
                DateTimeFormatter fmt12 = new DateTimeFormatterBuilder()
                        .parseCaseInsensitive()
                        .appendPattern("hh:mm:ss a")
                        .toFormatter(Locale.ENGLISH);
                return LocalTime.parse(timeText.trim(), fmt12);
            } catch (Exception e2) {
                log.error("Unable to parse time: '{}'", timeText);
                return null;
            }
        }
    }

    /**
     * Calculate time until next trigger in specified timezone
     */
    public long getSecondsUntilNextTrigger(Automation.Condition condition, String timezoneId) {
        ZoneId zone = resolveTimezone(timezoneId);
        ZonedDateTime now = ZonedDateTime.now(zone);

        LocalTime targetTime = parseTime(condition.getTime());
        if (targetTime == null) return -1;

        ZonedDateTime nextTrigger = now.with(targetTime);

        // If target time has passed today, schedule for tomorrow
        if (nextTrigger.isBefore(now)) {
            nextTrigger = nextTrigger.plusDays(1);
        }

        // If days are specified, find next matching day
        if (condition.getDays() != null && !condition.getDays().isEmpty()) {
            while (!condition.getDays().contains(getDayAbbreviation(nextTrigger))) {
                nextTrigger = nextTrigger.plusDays(1);
            }
        }

        return ChronoUnit.SECONDS.between(now, nextTrigger);
    }

    /**
     * Get day abbreviation (Mon, Tue, etc.)
     */
    private String getDayAbbreviation(ZonedDateTime dateTime) {
        String day = dateTime.getDayOfWeek()
                .getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)
                .substring(0, 3);
        return day.substring(0, 1).toUpperCase() + day.substring(1).toLowerCase();
    }

    /**
     * Validate timezone string
     */
    public boolean isValidTimezone(String timezoneId) {
        if (timezoneId == null || timezoneId.isEmpty()) {
            return false;
        }

        try {
            resolveTimezone(timezoneId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get timezone offset from UTC in hours
     */
    public String getTimezoneOffset(String timezoneId) {
        ZoneId zone = resolveTimezone(timezoneId);
        ZonedDateTime now = ZonedDateTime.now(zone);

        long offsetSeconds = now.getOffset().getTotalSeconds();
        long offsetHours = offsetSeconds / 3600;
        long offsetMinutes = Math.abs((offsetSeconds % 3600) / 60);

        if (offsetMinutes == 0) {
            return String.format("UTC%+d", offsetHours);
        } else {
            return String.format("UTC%+d:%02d", offsetHours, offsetMinutes);
        }
    }
}
