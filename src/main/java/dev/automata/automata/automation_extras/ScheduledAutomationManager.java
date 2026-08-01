package dev.automata.automata.automation_extras;

import dev.automata.automata.model.Automation;
import dev.automata.automata.repository.AutomationRepository;
import dev.automata.automata.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledAutomationManager {

    private final AutomationRepository automationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final RedisService redisService;
    private final TaskScheduler taskScheduler;  // injected Spring bean

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    // FIX 1: automationId → ALL futures owned by that automation (was a single
    // ScheduledFuture, but range/solar registration can produce 2+ jobs per
    // automation — the old single-value map silently dropped all but one).
    private final Map<String, List<ScheduledFuture<?>>> scheduledJobs = new ConcurrentHashMap<>();

    // FIX 3: interval (seconds) for the mixed data+schedule periodic re-check tick.
    private static final long MIXED_AUTOMATION_TICK_MS = 15_000L;

    // ── Startup: register all schedule-only automations ──────────────────

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        log.info("ScheduledAutomationManager: registering schedule-based automations...");
        automationRepository.findAll().stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsEnabled()))
                .filter(this::hasOnlyScheduledConditions)
                .forEach(this::register);
        log.info("ScheduledAutomationManager: {} automation(s) registered with {} total job(s)",
                scheduledJobs.size(),
                scheduledJobs.values().stream().mapToInt(List::size).sum());
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

    /**
     * FIX 1: cancels EVERY job owned by this automation, not just one.
     * Previously scheduledJobs was never populated by register(), so this
     * was always a no-op for cron/interval/solar jobs created there — every
     * refresh() silently piled up new duplicate jobs on top of old ones.
     */
    public void cancel(String automationId) {
        List<ScheduledFuture<?>> jobs = scheduledJobs.remove(automationId);
        if (jobs == null || jobs.isEmpty()) return;

        int cancelled = 0;
        for (ScheduledFuture<?> job : jobs) {
            if (job != null && !job.isDone()) {
                job.cancel(false);
                cancelled++;
            }
        }
        log.info("🛑 Cancelled {} scheduled job(s) for automation '{}'", cancelled, automationId);
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
            // FIX 1: actually store the futures — this line was commented out
            // before, so cancel()/refresh() could never stop these jobs.
            scheduledJobs.put(automation.getId(), futures);
            log.info("Registered {} job(s) for '{}'", futures.size(), automation.getName());
        }
    }


    // ── Exact time ("at") ─────────────────────────────────────────────────

    private void registerExact(Automation automation, Automation.Condition c,
                               List<ScheduledFuture<?>> futures) {
        LocalTime t = parseTime(c.getTime());
        if (t == null) {
            log.warn("Cannot parse time '{}' for automation '{}'", c.getTime(), automation.getName());
            return;
        }

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

    private void registerRange(Automation automation, Automation.Condition c,
                               List<ScheduledFuture<?>> futures) {
        LocalTime from = parseTime(c.getFromTime());
        LocalTime to = parseTime(c.getToTime());
        if (from == null || to == null) {
            log.warn("Cannot parse range times for automation '{}'", automation.getName());
            return;
        }

        String cronDays = toCronDays(c.getDays());

        String cronEnter = String.format("%d %d %d * * %s",
                from.getSecond(), from.getMinute(), from.getHour(), cronDays);
        futures.add(taskScheduler.schedule(
                () -> fireAutomation(automation, "range enter " + c.getFromTime()),
                new CronTrigger(cronEnter, IST)));

        String cronExit = String.format("%d %d %d * * %s",
                to.getSecond(), to.getMinute(), to.getHour(), cronDays);
        futures.add(taskScheduler.schedule(
                () -> fireAutomation(automation, "range exit " + c.getToTime()),
                new CronTrigger(cronExit, IST)));

        log.debug("'{}' — range enter: {} exit: {}", automation.getName(), cronEnter, cronExit);
    }


    // ── Interval ──────────────────────────────────────────────────────────

    private void registerInterval(Automation automation, Automation.Condition c,
                                  List<ScheduledFuture<?>> futures) {
        if (c.getIntervalMinutes() <= 0) {
            log.warn("Interval <= 0 on automation '{}', skipping", automation.getName());
            return;
        }

        ScheduledFuture<?> future = taskScheduler.scheduleAtFixedRate(
                () -> fireAutomation(automation, "interval/" + c.getIntervalMinutes() + "min"),
                Instant.now().plusSeconds(5),
                Duration.ofMinutes(c.getIntervalMinutes()));

        futures.add(future);
        log.debug("'{}' — interval every {} min", automation.getName(), c.getIntervalMinutes());
    }


    // ── Solar ─────────────────────────────────────────────────────────────

    private void registerSolar(Automation automation, Automation.Condition c,
                               List<ScheduledFuture<?>> futures) {
        ScheduledFuture<?> midnightJob = taskScheduler.schedule(
                () -> scheduleSolarFireForToday(automation, c),
                new CronTrigger("0 0 0 * * *", IST));
        futures.add(midnightJob);

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

            ScheduledFuture<?> oneShot = taskScheduler.schedule(
                    () -> fireAutomation(automation, "solar/" + c.getSolarType()
                            + " +" + c.getOffsetMinutes() + "min"),
                    fireAt.toInstant());

            // FIX 1 (secondary): today's one-shot solar job is now also tracked,
            // appended to the existing list rather than lost. Without this, a
            // cancel() right before the solar fire time would leave today's
            // one-shot still armed.
            scheduledJobs.computeIfAbsent(automation.getId(), k -> new ArrayList<>()).add(oneShot);

            log.info("'{}' — solar fire scheduled for {}", automation.getName(), fireAt);
        } catch (Exception e) {
            log.error("Failed to schedule solar job for '{}': {}",
                    automation.getName(), e.getMessage());
        }
    }

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


    // ── FIX 3: periodic re-check for MIXED data+schedule automations ──────
    //
    // hasOnlyScheduledConditions() only registers pure-schedule automations
    // above (init()/register()). An automation like "Light On" — a DURATION
    // distance gate feeding three scheduled leaf branches — is neither pure
    // schedule (fails hasOnlyScheduledConditions, the gate is data-driven)
    // nor purely data-driven (hasAnyScheduledConditions is true). Without a
    // periodic tick, its internal schedule windows (e.g. node_condition_16
    // closing at 01:40 AM) only get re-evaluated whenever the distance sensor
    // happens to publish a new reading — which could be minutes or hours
    // late relative to the actual window boundary.
    //
    // This tick finds exactly that middle category and re-fires them on a
    // short fixed interval using the existing PeriodicCheckEvent path, with
    // the automation's last known trigger-device payload (so DURATION/edge
    // memory isn't disturbed by a synthetic empty payload).

    @Scheduled(fixedRate = MIXED_AUTOMATION_TICK_MS)
    public void tickMixedAutomations() {
        List<Automation> mixed = automationRepository.findAll().stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsEnabled()))
                .filter(this::isMixedDataAndSchedule)
                .toList();

        if (mixed.isEmpty()) return;

        for (Automation automation : mixed) {
            try {
                fireAutomation(automation, "periodic-mixed-tick");
            } catch (Exception e) {
                log.error("❌ [mixed-tick] Failed for '{}': {}",
                        automation.getName(), e.getMessage(), e);
            }
        }
        log.debug("⏱️ [mixed-tick] Re-checked {} mixed data+schedule automation(s)", mixed.size());
    }

    /**
     * True if the automation has at least one scheduled condition AND at
     * least one non-scheduled (data-driven) condition somewhere in its tree —
     * i.e. it falls in the gap between hasOnlyScheduledConditions() (pure
     * schedule, handled by cron jobs above) and isPurelyDataDriven() (handled
     * by live device events only).
     */
    private boolean isMixedDataAndSchedule(Automation a) {
        return hasAnyScheduledConditions(a) && !hasOnlyScheduledConditions(a);
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
     * Returns true if ALL enabled trigger conditions (non-gate/root) are "scheduled".
     * These automations don't need sensor data — they run purely on time.
     */
    public boolean hasOnlyScheduledConditions(Automation automation) {
        if (automation.getConditions() == null || automation.getConditions().isEmpty())
            return false;

        List<Automation.Condition> triggerConditions = getRootConditions(automation);

        return !triggerConditions.isEmpty()
                && triggerConditions.stream()
                .allMatch(c -> "scheduled".equals(c.getCondition()));
    }

    /**
     * FIX 2: root/trigger conditions are now those whose previousNodeRef does
     * NOT point at another enabled condition — matching ExecutionPlanCompiler's
     * actual root-detection logic (see ExecutionPlanCompiler Step 3b), not the
     * old operator-node model. The old isGateCondition() only recognized a
     * condition as "gated" if its parent was an operator node; automations
     * saved under the current condition→condition chaining model (e.g. "Light
     * On": node_condition_16/21/33 → previousNodeRef points at node_condition_1,
     * a condition, not an operator) were misclassified as independent root
     * triggers instead of children of the distance gate.
     */
    private List<Automation.Condition> getRootConditions(Automation automation) {
        Set<String> enabledConditionIds = automation.getConditions().stream()
                .filter(Automation.Condition::isEnabled)
                .map(Automation.Condition::getNodeId)
                .collect(Collectors.toSet());

        return automation.getConditions().stream()
                .filter(Automation.Condition::isEnabled)
                .filter(c -> !isChildOfAnotherCondition(c, enabledConditionIds))
                .toList();
    }

    private boolean isChildOfAnotherCondition(Automation.Condition c, Set<String> enabledConditionIds) {
        if (c.getPreviousNodeRef() == null) return false;
        return c.getPreviousNodeRef().stream()
                .anyMatch(ref -> ref.getNodeId() != null
                        && enabledConditionIds.contains(ref.getNodeId()));
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
                .collect(Collectors.joining(","));
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

    public boolean isScheduled(String automationId) {
        List<ScheduledFuture<?>> jobs = scheduledJobs.get(automationId);
        return jobs != null && jobs.stream().anyMatch(j -> !j.isDone());
    }


    // ── Condition type helpers ────────────────────────────────────────────

    /**
     * Returns true if the automation has AT LEAST ONE scheduled condition
     * anywhere in its tree (root or child).
     */
    public boolean hasAnyScheduledConditions(Automation a) {
        if (a.getConditions() == null || a.getConditions().isEmpty()) return false;
        return a.getConditions().stream()
                .filter(Automation.Condition::isEnabled)
                .anyMatch(c -> "scheduled".equals(c.getCondition()));
    }

    /**
     * Returns true if the automation is purely data-driven — no scheduled
     * conditions anywhere. Such automations should ONLY be evaluated on live
     * device events, never polled.
     */
    public boolean isPurelyDataDriven(Automation a) {
        return !hasAnyScheduledConditions(a);
    }
}