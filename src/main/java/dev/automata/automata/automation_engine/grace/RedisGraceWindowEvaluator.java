package dev.automata.automata.automation_engine.grace;

import dev.automata.automata.automation_engine.AutomationStateStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisGraceWindowEvaluator implements GraceWindowEvaluator {

    private final AutomationStateStore stateStore;

    @Override
    public GraceDecision evaluate(String automationId, String nodeId, long durationMs,
                                  boolean wasActive, long nowMs) {

        Long armedAt = stateStore.getGraceArmedAtEpochMs(automationId, nodeId);

        if (armedAt == null) {
            if (!wasActive) {
                // Nothing to hold — let it fail/proceed normally.
                return new GraceDecision(false, false);
            }
            // First false tick after being active — arm the clock and hold.
            long ttlSeconds = (durationMs / 1000L) + 5L; // 5s buffer for clock jitter
            stateStore.armGrace(automationId, nodeId, nowMs, ttlSeconds);
            log.info("⏳ [{}] Node '{}' — arming {}ms grace before negative actions",
                    automationId, nodeId, durationMs);
            return new GraceDecision(true, true);
        }

        if (nowMs - armedAt < durationMs) {
            // Still inside the window — keep holding.
            return new GraceDecision(true, false);
        }

        // Grace expired — release it.
        stateStore.clearGrace(automationId, nodeId);
        log.info("⏰ [{}] Node '{}' grace window expired — releasing", automationId, nodeId);
        return new GraceDecision(false, false);
    }
}