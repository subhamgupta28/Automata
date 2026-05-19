package dev.automata.automata.dto;

import dev.automata.automata.v2.ExecutionPlan;
import lombok.Getter;

/**
 * Immutable decision record produced by AutomationEvaluator.handleBranches()
 * for each gate branch evaluated in a given tick.
 * <p>
 * One BranchDecision is produced per branch per evaluation tick.
 * AutomationOrchestrator reads the list of decisions to:
 * 1. Compute the next AutomationRuntimeState (applyBranchDecisions)
 * 2. Route dispatch: TRIGGER → positive actions, REVERT/DURATION_EXPIRED → negative actions
 * <p>
 * Decision flow per branch per tick:
 * <p>
 * Branch was ACTIVE/HOLDING:
 * gate=false OR lower priority wins  →  REVERT      (fire negative actions → IDLE)
 * gate=true AND still winner:
 * HOLDING + RUNNING key expired    →  DURATION_EXPIRED (fire negative → IDLE)
 * HOLDING + RUNNING key alive      →  KEEP_ACTIVE (no dispatch, no state write)
 * ACTIVE + still winner            →  KEEP_ACTIVE
 * <p>
 * Branch was IDLE:
 * gate=true AND is winner:
 * AND siblings all true            →  TRIGGER     (fire positive actions → ACTIVE/HOLDING)
 * AND siblings NOT all true        →  SUPPRESSED  (AND constraint not met)
 * gate=true AND NOT winner           →  SUPPRESSED  (lower priority)
 * gate=false                         →  (no decision added)
 * <p>
 * Real-life example — "Periodic Bat 500 charging" interval branch:
 * Tick 1  (interval ready): TRIGGER
 * Ticks 2-100 (running):    KEEP_ACTIVE
 * Tick 101 (run expired):   DURATION_EXPIRED → fires [channel1=false, "charging stopped"]
 * Ticks 102-149 (cooldown): (no decision — IDLE, gate=false)
 * Tick 150 (interval ready again): TRIGGER
 */
@Getter
public class BranchDecision {

    public enum Type {
        TRIGGER,          // Gate is true, branch was IDLE → fire positive actions
        REVERT,           // Gate turned false or lost priority → fire negative actions
        DURATION_EXPIRED, // RUNNING key expired → fire negative actions (duration ended)
        KEEP_ACTIVE,      // Gate still true, branch already active → no action needed
        SUPPRESSED        // Gate true but AND siblings not met / lower priority
    }

    private final Type type;
    private final ExecutionPlan.CompiledBranch branch;
    private final String reason;

    private BranchDecision(Type type, ExecutionPlan.CompiledBranch branch, String reason) {
        this.type = type;
        this.branch = branch;
        this.reason = reason;
    }


    // ─────────────────────────────────────────────────────────────────────
    // STATIC FACTORIES
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Branch gate just became true and this is the highest-priority winner.
     * Orchestrator will dispatch branch.positiveActions and advance state
     * to ACTIVE (or HOLDING if durationMinutes > 0).
     */
    public static BranchDecision trigger(ExecutionPlan.CompiledBranch branch) {
        return new BranchDecision(Type.TRIGGER, branch, "Gate true — triggering");
    }

    /**
     * Branch was active but its gate turned false or was outranked.
     * Orchestrator will dispatch branch.negativeActions and set state to IDLE.
     *
     * @param reason human-readable explanation logged to AutomationLog
     */
    public static BranchDecision revert(ExecutionPlan.CompiledBranch branch, String reason) {
        return new BranchDecision(Type.REVERT, branch, reason);
    }

    /**
     * Branch was HOLDING and the RUNNING key has expired (durationMinutes elapsed).
     * Orchestrator dispatches branch.negativeActions, deletes the RUNNING key,
     * and sets state to IDLE.
     * <p>
     * Real-life: "Periodic Bat 500 charging" 20-min duration window expired
     * → fires [channel1=false (LiFePO4), app_notify "charging stopped"]
     */
    public static BranchDecision durationExpired(ExecutionPlan.CompiledBranch branch) {
        return new BranchDecision(Type.DURATION_EXPIRED, branch,
                "Duration expired — RUNNING key gone");
    }

    /**
     * Branch is still active and its gate is still true — nothing to do.
     * No dispatch, no state write. Evaluator outcome becomes SKIPPED.
     */
    public static BranchDecision keepActive(ExecutionPlan.CompiledBranch branch) {
        return new BranchDecision(Type.KEEP_ACTIVE, branch, "Gate still true — staying active");
    }

    /**
     * Branch gate is true but the branch cannot TRIGGER because either:
     * (a) a higher-priority branch already won (OR semantics), or
     * (b) this branch requires AND and one or more sibling gates are false.
     *
     * @param reason winning branch's gateNodeId (case a) or "AND: sibling not met" (case b)
     */
    public static BranchDecision suppressed(ExecutionPlan.CompiledBranch branch, String reason) {
        return new BranchDecision(Type.SUPPRESSED, branch, reason);
    }


    // ─────────────────────────────────────────────────────────────────────
    // DISPLAY
    // ─────────────────────────────────────────────────────────────────────

    @Override
    public String toString() {
        return "BranchDecision{" + type + ", gate=" + branch.getGateNodeId()
                + (reason != null ? ", reason='" + reason + "'" : "") + "}";
    }
}