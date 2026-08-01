package dev.automata.automata.model;

public enum FiringMode {
    EVERY_TICK,       // fire every evaluation while conditions hold true
    ON_STATE_CHANGE   // fire once on the not-met -> met transition (default)
}