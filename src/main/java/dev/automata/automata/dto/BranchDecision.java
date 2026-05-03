package dev.automata.automata.dto;

import dev.automata.automata.v2.ExecutionPlan;

@lombok.Value
public class BranchDecision {
    public enum Type {TRIGGER, REVERT, KEEP_ACTIVE, DURATION_EXPIRED, SUPPRESSED, COOLDOWN}

    Type type;
    ExecutionPlan.CompiledBranch branch;
    String reason;

    public static BranchDecision trigger(ExecutionPlan.CompiledBranch b) {
        return new BranchDecision(Type.TRIGGER, b, "Gate true");
    }

    public static BranchDecision revert(ExecutionPlan.CompiledBranch b, String reason) {
        return new BranchDecision(Type.REVERT, b, reason);
    }

    public static BranchDecision keepActive(ExecutionPlan.CompiledBranch b) {
        return new BranchDecision(Type.KEEP_ACTIVE, b, "Still active");
    }

    public static BranchDecision durationExpired(ExecutionPlan.CompiledBranch b) {
        return new BranchDecision(Type.DURATION_EXPIRED, b, "Duration timer expired");
    }

    public static BranchDecision suppressed(ExecutionPlan.CompiledBranch b, String byNode) {
        return new BranchDecision(Type.SUPPRESSED, b, "Suppressed by " + byNode);
    }

    public static BranchDecision cooldown(ExecutionPlan.CompiledBranch b) {
        return new BranchDecision(Type.COOLDOWN, b, "Interval cooldown");
    }
}