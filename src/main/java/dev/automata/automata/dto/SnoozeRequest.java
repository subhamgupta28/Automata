package dev.automata.automata.dto;

import lombok.Data;

@Data
public class SnoozeRequest {
    private String automationId;
    private SnoozeType type;      // SNOOZE | DISABLE
    private int durationMinutes;  // 0 = permanent (for DISABLE)

    public enum SnoozeType {SNOOZE, DISABLE}
}
