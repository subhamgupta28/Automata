package dev.automata.automata.v2;

import dev.automata.automata.dto.AutomationRuntimeState;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Pure stateless component — reads coalition config and runtime state,
 * returns a CoalitionResult. No Redis writes, no side effects.
 * <p>
 * Called by AutomationOrchestrator.execute() after recording the firing
 * member and before calling AutomationEvaluator.evaluate().
 * <p>
 * Coalition modes
 * ───────────────
 * ANY      — always passes (evaluation runs whenever any member fires;
 * this is the legacy single-trigger behaviour).
 * <p>
 * ALL      — all non-veto members must have fired within windowSeconds.
 * If any member's lastFired is 0 or older than the window,
 * the coalition is NOT satisfied → skip evaluation this tick.
 * <p>
 * SEQUENCE — non-veto members must fire in ascending sequenceIndex order,
 * each within windowSeconds of the previous.
 * state.sequenceProgress tracks the next expected index.
 * When the firing member matches sequenceProgress, the index
 * advances. When the last member fires, the coalition is satisfied
 * and sequenceProgress resets to 0.
 * An out-of-order fire, or a gap exceeding windowSeconds, resets
 * sequenceProgress to 0.
 * <p>
 * Veto
 * ────
 * Checked first for all modes. If any veto member's lastFired is within
 * windowSeconds of now, the coalition is VETOED and evaluation is skipped
 * regardless of mode.
 * <p>
 * Payload selection
 * ─────────────────
 * The member with role="primary" supplies the evaluation payload.
 * For backward compat, if no coalition is configured (plan.triggerCoalition==null),
 * the orchestrator skips this guard entirely and uses the incoming payload as-is.
 */
@Slf4j
@Component
public class CoalitionGuard {

    public enum CoalitionStatus {
        /**
         * Proceed with evaluation.
         */
        SATISFIED,
        /**
         * Skip evaluation — quorum / sequence not yet met.
         */
        NOT_YET,
        /**
         * Skip evaluation — a veto member fired recently.
         */
        VETOED,
        /**
         * No coalition configured — proceed (legacy path).
         */
        LEGACY
    }

    public record CoalitionResult(CoalitionStatus status, String reason) {
        public boolean shouldProceed() {
            return status == CoalitionStatus.SATISFIED || status == CoalitionStatus.LEGACY;
        }
    }

    /**
     * @param coalition      the plan's coalition config (may be null → legacy)
     * @param firingDeviceId the device that just fired (triggered this execution)
     * @param state          current runtime state (contains lastFired timestamps, sequenceProgress)
     * @param nowMs          current epoch-ms (passed in for testability)
     */
    public CoalitionResult evaluate(TriggerCoalition coalition,
                                    String firingDeviceId,
                                    AutomationRuntimeState state,
                                    long nowMs) {
        if (coalition == null) {
            return new CoalitionResult(CoalitionStatus.LEGACY, "no coalition configured");
        }

        // ── Veto check (applies to all modes) ─────────────────────────────
        for (TriggerMember veto : coalition.getVetoMembers()) {
            long lastFired = state.getMemberLastFired(veto.getDeviceId());
            if (lastFired > 0 && (nowMs - lastFired) < (long) coalition.getWindowSeconds() * 1000) {
                String msg = "Veto member '" + veto.getDeviceId() + "' fired "
                        + ((nowMs - lastFired) / 1000) + "s ago (window="
                        + coalition.getWindowSeconds() + "s)";
                log.debug("🚫 Coalition VETOED: {}", msg);
                return new CoalitionResult(CoalitionStatus.VETOED, msg);
            }
        }

        return switch (coalition.getMode()) {

            case ANY -> new CoalitionResult(CoalitionStatus.SATISFIED,
                    "ANY mode — member '" + firingDeviceId + "' fired");

            case ALL -> evaluateAll(coalition, state, nowMs);

            case SEQUENCE -> evaluateSequence(coalition, firingDeviceId, state, nowMs);
        };
    }

    // ─────────────────────────────────────────────────────────────────────
    // ALL MODE
    // ─────────────────────────────────────────────────────────────────────

    private CoalitionResult evaluateAll(TriggerCoalition coalition,
                                        AutomationRuntimeState state,
                                        long nowMs) {
        List<TriggerMember> required = coalition.getNonVetoMembers();
        long windowMs = (long) coalition.getWindowSeconds() * 1000;

        for (TriggerMember member : required) {
            long lastFired = state.getMemberLastFired(member.getDeviceId());
            if (lastFired == 0 || (nowMs - lastFired) > windowMs) {
                String msg = "ALL: member '" + member.getDeviceId() + "' not yet fired within "
                        + coalition.getWindowSeconds() + "s";
                log.debug("⏳ Coalition NOT_YET: {}", msg);
                return new CoalitionResult(CoalitionStatus.NOT_YET, msg);
            }
        }

        return new CoalitionResult(CoalitionStatus.SATISFIED,
                "ALL: all " + required.size() + " members fired within window");
    }

    // ─────────────────────────────────────────────────────────────────────
    // SEQUENCE MODE
    // ─────────────────────────────────────────────────────────────────────

    private CoalitionResult evaluateSequence(TriggerCoalition coalition,
                                             String firingDeviceId,
                                             AutomationRuntimeState state,
                                             long nowMs) {
        List<TriggerMember> ordered = coalition.getNonVetoMembers().stream()
                .sorted(java.util.Comparator.comparingInt(TriggerMember::getSequenceIndex))
                .toList();

        if (ordered.isEmpty()) {
            return new CoalitionResult(CoalitionStatus.SATISFIED, "SEQUENCE: no members");
        }

        int progress = state.getSequenceProgress();
        long windowMs = (long) coalition.getWindowSeconds() * 1000;

        // Check if the current progress has timed out
        if (progress > 0) {
            TriggerMember lastExpected = ordered.get(progress - 1);
            long lastFired = state.getMemberLastFired(lastExpected.getDeviceId());
            if (lastFired > 0 && (nowMs - lastFired) > windowMs) {
                // Sequence timed out — this will reset in computeNextState
                log.debug("⏰ Coalition SEQUENCE timed out at step {} — resetting", progress);
                return new CoalitionResult(CoalitionStatus.NOT_YET,
                        "SEQUENCE: timed out at step " + progress + ", resetting");
            }
        }

        // Check if the firing device matches the expected next member
        if (progress >= ordered.size()) {
            // Sequence was already complete — restart
            return new CoalitionResult(CoalitionStatus.NOT_YET,
                    "SEQUENCE: already completed, awaiting reset");
        }

        TriggerMember expected = ordered.get(progress);
        if (!expected.getDeviceId().equals(firingDeviceId)) {
            // Out-of-order — not the expected next member
            log.debug("🔀 Coalition SEQUENCE: expected '{}' at step {} but got '{}'",
                    expected.getDeviceId(), progress, firingDeviceId);
            return new CoalitionResult(CoalitionStatus.NOT_YET,
                    "SEQUENCE: out of order — expected '" + expected.getDeviceId()
                            + "' at step " + progress);
        }

        // This member is the expected next in sequence
        int nextProgress = progress + 1;
        if (nextProgress >= ordered.size()) {
            // Final member — sequence complete
            return new CoalitionResult(CoalitionStatus.SATISFIED,
                    "SEQUENCE: final member '" + firingDeviceId + "' fired — sequence complete");
        }

        // Sequence in progress but not yet complete
        return new CoalitionResult(CoalitionStatus.NOT_YET,
                "SEQUENCE: step " + nextProgress + "/" + ordered.size()
                        + " — waiting for '" + ordered.get(nextProgress).getDeviceId() + "'");
    }
}