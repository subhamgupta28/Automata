package dev.automata.automata.automation_engine.dto;

public record GraceUpdate(boolean shouldArm, boolean shouldClear, long armedAtEpochMs, long ttlSeconds) {
    static GraceUpdate arm(long armedAtEpochMs, long ttlSeconds) {
        return new GraceUpdate(true, false, armedAtEpochMs, ttlSeconds);
    }

    static GraceUpdate clear() {
        return new GraceUpdate(false, true, 0, 0);
    }
}
