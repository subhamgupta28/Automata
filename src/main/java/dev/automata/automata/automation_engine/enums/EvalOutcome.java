package dev.automata.automata.automation_engine.enums;

public enum EvalOutcome {
    TRIGGERED,
    /**
     * An OR fanout branch transitioned inactive→active and dispatched its own
     * per-branch positive actions. The top-level automation state is ACTIVE
     * (or becomes ACTIVE), but the trigger came from a specific branch node
     * rather than the automation as a whole.
     * <p>
     * Distinct from TRIGGERED so the orchestrator knows NOT to reset
     * topLevelState on every BRANCH_TRIGGERED — topLevelState is already
     * ACTIVE and should remain so until ALL branches fail (C1_NEGATIVE).
     */
    BRANCH_TRIGGERED,
    C1_NEGATIVE,
    SKIPPED,
    NOT_MET,
    STATELESS_FIRE,
    FALLBACK
}
