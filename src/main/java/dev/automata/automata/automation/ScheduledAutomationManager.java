package dev.automata.automata.automation;

import dev.automata.automata.model.Automation;
import dev.automata.automata.repository.AutomationRepository;
import dev.automata.automata.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledAutomationManager {

    private final AutomationRepository automationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final RedisService redisService;
    private final TaskScheduler taskScheduler;  // injected Spring bean

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    // automationId → list of running futures (one automation can have multiple jobs)
    private final Map<String, List<ScheduledFuture<?>>> scheduledJobs = new ConcurrentHashMap<>();


    // ── Startup: register all schedule-only automations ──────────────────

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        log.info("ScheduledAutomationManager: registering schedule-based automations...");
        automationRepository.findAll().stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsEnabled()))
                .filter(this::hasOnlyScheduledConditions)
                .forEach(this::register);
        log.info("ScheduledAutomationManager: {} automations registered",
                scheduledJobs.size());
    }


    // ── Public API — called from AutomationService on save/disable ────────

    /**
     * Cancel existing jobs for this automation and re-register with fresh config.
     * Call this whenever an automation is saved or its enabled flag changes.
     */
    public void refresh(Automation automation) {
        cancel(automation.getId());
        if (Boolean.TRUE.equals(automation.getIsEnabled())
                && hasOnlyScheduledConditions(automation)) {
            register(automation);
        }
    }

    public void cancel(String automationId) {
        List<ScheduledFuture<?>> futures = scheduledJobs.remove(automationId);
        if (futures != null) {
            futures.forEach(f -> f.cancel(false));
            log.debug("Cancelled {} scheduled job(s) for automation {}",
                    futures.size(), automationId);
        }
    }


    // ── Registration ──────────────────────────────────────────────────────

    private void register(Automation automation) {
        List<ScheduledFuture<?>> futures = new ArrayList<>();

        for (Automation.Condition c : automation.getConditions()) {
            if (!c.isEnabled() || !"scheduled".equals(c.getCondition())) continue;

            String schedType = c.getScheduleType() != null ? c.getScheduleType() : "at";

            switch (schedType) {
                case "at" -> registerExact(automation, c, futures);
                case "range" -> registerRange(automation, c, futures);
                case "interval" -> registerInterval(automation, c, futures);
                case "solar" -> registerSolar(automation, c, futures);
                default -> log.warn("Unknown scheduleType '{}' on automation '{}'",
                        schedType, automation.getName());
            }
        }

        if (!futures.isEmpty()) {
            scheduledJobs.put(automation.getId(), futures);
            log.info("Registered {} job(s) for '{}'", futures.size(), automation.getName());
        }
    }


    // ── Exact time ("at") ─────────────────────────────────────────────────
    // Uses a daily cron expression.  Spring's CronTrigger handles day-of-week
    // via the days list so we don't need to compute that ourselves.

    private void registerExact(Automation automation, Automation.Condition c,
                               List<ScheduledFuture<?>> futures) {
        LocalTime t = parseTime(c.getTime());
        if (t == null) {
            log.warn("Cannot parse time '{}' for automation '{}'", c.getTime(), automation.getName());
            return;
        }

        // cron: second minute hour * * DAY_OF_WEEK
        String cron = String.format("%d %d %d * * %s",
                t.getSecond(), t.getMinute(), t.getHour(),
                toCronDays(c.getDays()));

        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> fireAutomation(automation, "scheduled/at " + c.getTime()),
                new CronTrigger(cron, IST));

        futures.add(future);
        log.debug("'{}' — exact cron: {}", automation.getName(), cron);
    }


    // ── Time range ("range") ──────────────────────────────────────────────
    // Two cron jobs: one fires at fromTime (enter), one at toTime (exit).
    // AutomationService handles state via ACTIVE/IDLE — we just fire at both edges.

    private void registerRange(Automation automation, Automation.Condition c,
                               List<ScheduledFuture<?>> futures) {
        LocalTime from = parseTime(c.getFromTime());
        LocalTime to = parseTime(c.getToTime());
        if (from == null || to == null) {
            log.warn("Cannot parse range times for automation '{}'", automation.getName());
            return;
        }

        String cronDays = toCronDays(c.getDays());

        // Enter edge — fire checkAndExecute; condition will be TRUE, state → ACTIVE
        String cronEnter = String.format("%d %d %d * * %s",
                from.getSecond(), from.getMinute(), from.getHour(), cronDays);
        futures.add(taskScheduler.schedule(
                () -> fireAutomation(automation, "range enter " + c.getFromTime()),
                new CronTrigger(cronEnter, IST)));

        // Exit edge — fire checkAndExecute; condition will be FALSE, state → IDLE
        String cronExit = String.format("%d %d %d * * %s",
                to.getSecond(), to.getMinute(), to.getHour(), cronDays);
        futures.add(taskScheduler.schedule(
                () -> fireAutomation(automation, "range exit " + c.getToTime()),
                new CronTrigger(cronExit, IST)));

        log.debug("'{}' — range enter: {} exit: {}", automation.getName(), cronEnter, cronExit);
    }


    // ── Interval ──────────────────────────────────────────────────────────
    // Schedules at fixed rate. Duration (if any) is handled by AutomationService
    // via the RUNNING Redis key — no change needed there.

    private void registerInterval(Automation automation, Automation.Condition c,
                                  List<ScheduledFuture<?>> futures) {
        if (c.getIntervalMinutes() <= 0) {
            log.warn("Interval <= 0 on automation '{}', skipping", automation.getName());
            return;
        }

        long rateMs = c.getIntervalMinutes() * 60_000L;

        // Start immediately, repeat every intervalMinutes
        ScheduledFuture<?> future = taskScheduler.scheduleAtFixedRate(
                () -> fireAutomation(automation, "interval/" + c.getIntervalMinutes() + "min"),
                Instant.now().plusSeconds(5),   // small initial delay to let app fully start
                Duration.ofMinutes(c.getIntervalMinutes()));

        futures.add(future);
        log.debug("'{}' — interval every {} min", automation.getName(), c.getIntervalMinutes());
    }


    // ── Solar ─────────────────────────────────────────────────────────────
    // Solar times change daily. We schedule a daily job at midnight that
    // fetches sunrise/sunset, computes the adjusted time, then schedules
    // a one-shot job for that day.

    private void registerSolar(Automation automation, Automation.Condition c,
                               List<ScheduledFuture<?>> futures) {
        // Daily midnight job to re-schedule for today's solar time
        // cron: 0 0 0 * * * (every day at midnight)
        ScheduledFuture<?> midnightJob = taskScheduler.schedule(
                () -> scheduleSolarFireForToday(automation, c),
                new CronTrigger("0 0 0 * * *", IST));
        futures.add(midnightJob);

        // Also schedule for today immediately on startup (don't wait until midnight)
        scheduleSolarFireForToday(automation, c);

        log.debug("'{}' — solar {} +{}min registered", automation.getName(),
                c.getSolarType(), c.getOffsetMinutes());
    }

    private void scheduleSolarFireForToday(Automation automation, Automation.Condition c) {
        try {
            LocalTime solarTime = getSunTimeFromRedis(c.getSolarType());
            if (solarTime == null) {
                log.warn("Solar time unavailable for '{}', will retry tomorrow",
                        automation.getName());
                return;
            }

            LocalTime fireTime = solarTime.plusMinutes(c.getOffsetMinutes());
            ZonedDateTime fireAt = LocalDate.now(IST).atTime(fireTime).atZone(IST);

            if (fireAt.isBefore(ZonedDateTime.now(IST))) {
                log.debug("'{}' — solar fire time {} already passed today, skipping",
                        automation.getName(), fireTime);
                return;
            }

            // One-shot job for today
            taskScheduler.schedule(
                    () -> fireAutomation(automation, "solar/" + c.getSolarType()
                            + " +" + c.getOffsetMinutes() + "min"),
                    fireAt.toInstant());

            log.info("'{}' — solar fire scheduled for {}", automation.getName(), fireAt);
        } catch (Exception e) {
            log.error("Failed to schedule solar job for '{}': {}",
                    automation.getName(), e.getMessage());
        }
    }

    /**
     * Reads sunrise/sunset from the Redis cache populated by AutomationService.
     */
    private LocalTime getSunTimeFromRedis(String solarType) {
        String key = "SUN_TIME:" + solarType + "-" + LocalDate.now(IST);
        Object val = redisService.get(key);
        if (val != null) {
            try {
                return LocalTime.parse(val.toString());
            } catch (Exception ignored) {
            }
        }
        return null;
    }


    // ── Execution ─────────────────────────────────────────────────────────

    private void fireAutomation(Automation automation, String trigger) {
        log.debug("Firing '{}' via scheduled trigger: {}", automation.getName(), trigger);
        try {
            Map<String, Object> recentData =
                    redisService.getRecentDeviceData(automation.getTrigger().getDeviceId());
            eventPublisher.publishEvent(new PeriodicCheckEvent(this, automation, recentData, trigger));
        } catch (Exception e) {
            log.error("Error firing scheduled automation '{}': {}",
                    automation.getName(), e.getMessage(), e);
        }
    }


    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Returns true if ALL enabled trigger conditions (non-gate) are "scheduled".
     * These automations don't need sensor data — they run purely on time.
     */
    public boolean hasOnlyScheduledConditions(Automation automation) {
        if (automation.getConditions() == null || automation.getConditions().isEmpty())
            return false;

        Set<String> operatorIds = automation.getOperators() == null ? Set.of() :
                automation.getOperators().stream()
                .map(Automation.Operator::getNodeId)
                .collect(java.util.stream.Collectors.toSet());

        List<Automation.Condition> triggerConditions = automation.getConditions().stream()
                .filter(Automation.Condition::isEnabled)
                .filter(c -> !isGateCondition(c, operatorIds))
                .toList();

        return !triggerConditions.isEmpty()
                && triggerConditions.stream()
                .allMatch(c -> "scheduled".equals(c.getCondition()));
    }

    private boolean isGateCondition(Automation.Condition c, Set<String> operatorIds) {
        return c.getPreviousNodeRef() != null &&
                c.getPreviousNodeRef().stream()
                        .anyMatch(ref -> operatorIds.contains(ref.getNodeId()));
    }

    /**
     * Converts a days list like ["Mon","Tue","Sun"] to Spring cron day expression.
     * Spring cron uses: MON,TUE,WED,THU,FRI,SAT,SUN
     * "Everyday" → * (all days)
     */
    private String toCronDays(List<String> days) {
        if (days == null || days.isEmpty() || days.contains("Everyday")) return "*";
        return days.stream()
                .map(String::toUpperCase)
                .collect(java.util.stream.Collectors.joining(","));
    }

    private LocalTime parseTime(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalTime.parse(raw.trim(), DateTimeFormatter.ofPattern("HH:mm:ss"));
        } catch (Exception e1) {
            try {
                return LocalTime.parse(raw.trim(),
                        new DateTimeFormatterBuilder().parseCaseInsensitive()
                                .appendPattern("hh:mm:ss a").toFormatter(Locale.ENGLISH));
            } catch (Exception e2) {
                return null;
            }
        }
    }
}
