package dev.automata.automata.automation_engine;

import lombok.Builder;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Describes how multiple trigger devices must cooperate before an automation
 * is eligible for evaluation.
 * <p>
 * The coalition replaces the single triggerDeviceId on ExecutionPlan.
 * For backward compatibility, if triggerCoalition is null the evaluator
 * falls back to the legacy single-trigger path.
 * <p>
 * Coalition modes
 * ───────────────
 * ANY  — evaluation runs as soon as ANY member fires. This is identical to
 * the current behaviour for single-trigger automations and also
 * covers OR-like multi-sensor scenarios (motion sensor OR door sensor).
 * <p>
 * ALL  — ALL non-veto members must have fired within windowSeconds before the
 * evaluation is allowed to proceed.  Each member's last-fired timestamp
 * is stored in AutomationRuntimeState.triggerMemberLastFired.
 * Example: "turn on AC only when both temperature sensor AND occupancy
 * sensor have reported within the last 60 seconds".
 * <p>
 * SEQUENCE — members must fire in the declared list order, each within
 * windowSeconds of the previous.  Out-of-order or timed-out
 * sequences reset the sequence state.
 * Example: "alert when door opens THEN motion detected within 30s"
 * <p>
 * Veto members
 * ────────────
 * A member with role="veto" suppresses the automation entirely when it has
 * fired within windowSeconds, regardless of coalition mode.
 * Example: "don't run if the manual-override switch fired in the last 5 min".
 */
@Value
@Builder
public class TriggerCoalition {

    public enum CoalitionMode {ANY, ALL, SEQUENCE}

    CoalitionMode mode;

    /**
     * For ALL and SEQUENCE modes: all required members must have fired within
     * this many seconds for the quorum to be satisfied.
     * For ANY mode: ignored (each member fires independently).
     * For veto members: a veto is active if it fired within this window.
     */
    int windowSeconds;

    List<TriggerMember> members;

    // ── Helpers ───────────────────────────────────────────────────────────

    public List<TriggerMember> getNonVetoMembers() {
        if (members == null) return new ArrayList<>();
        return members.stream()
                .filter(m -> !"veto".equals(m.getRole()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public List<TriggerMember> getVetoMembers() {
        if (members == null) return new ArrayList<>();
        return members.stream()
                .filter(m -> "veto".equals(m.getRole()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public boolean hasVetoMembers() {
        return members != null && members.stream().anyMatch(m -> "veto".equals(m.getRole()));
    }
}