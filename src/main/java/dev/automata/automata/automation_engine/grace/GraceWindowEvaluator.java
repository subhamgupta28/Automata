package dev.automata.automata.automation_engine.grace;

/**
 * Single source of truth for the "hold true across a false blip until
 * durationMs elapses" state machine. Previously duplicated between
 * AutomationEvaluator.walkNode() (in-tree negative-action grace) and
 * AutomationOrchestrator.foldInStrandedNegativeActions() (stranded-node
 * grace) — both re-implemented the same armed/holding/expired logic
 * independently, risking drift when one copy is fixed and the other isn't.
 */
public interface GraceWindowEvaluator {

    /**
     * @param automationId automation this grace timer belongs to
     * @param nodeId       node this grace timer belongs to
     * @param durationMs   how long to hold before releasing
     * @param wasActive    whether the node/branch was active prior to this tick
     * @param nowMs        current epoch-ms
     */
    GraceDecision evaluate(String automationId, String nodeId, long durationMs,
                           boolean wasActive, long nowMs);

    record GraceDecision(
            boolean hold,     // true = treat this tick as still-active, suppress negatives
            boolean justArmed // true = this call started a new grace window (for logging)
    ) {
    }
}