package dev.automata.automata.dto;

public enum AutomationState {
    IDLE,        // nothing active
    ACTIVE,      // positive executed
    HOLDING      // waiting for duration expiry
}
