package dev.automata.automata.automation_engine;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * One participant in a TriggerCoalition.
 * <p>
 * Roles
 * ─────
 * "primary"   — the device whose payload is used as the primary payload for
 * condition evaluation. Exactly one member should be primary.
 * Maps 1-to-1 with the legacy trigger.deviceId.
 * <p>
 * "secondary" — an additional device whose data may be read by conditions
 * (CompiledCondition.deviceId). Does not supply the primary payload.
 * The automation can still evaluate when only a secondary fires,
 * but condition nodes referencing primary device keys read from
 * the cached Redis data for the primary device instead.
 * <p>
 * "veto"      — if this device fires within TriggerCoalition.windowSeconds,
 * the entire automation is suppressed for that window. The
 * orchestrator checks this before evaluation.
 * <p>
 * Sequence index
 * ──────────────
 * For SEQUENCE coalitions, sequenceIndex defines the expected firing order
 * (0-based). Members fire in ascending sequenceIndex order. Members with the
 * same sequenceIndex may fire in any order relative to each other.
 */
@Value
@Builder
public class TriggerMember {

    /**
     * MongoDB ObjectId of the triggering device.
     */
    String deviceId;

    /**
     * Payload keys this member publishes that are relevant to this automation.
     */
    List<String> keys;

    /**
     * "primary", "secondary", or "veto".
     * Only "primary" members supply the evaluation payload directly.
     */
    String role;

    /**
     * For SEQUENCE coalitions: 0-based position in the expected firing order.
     * Ignored for ANY and ALL modes.
     */
    int sequenceIndex;

    // ── Helpers ───────────────────────────────────────────────────────────

    public boolean isPrimary() {
        return "primary".equals(role);
    }

    public boolean isSecondary() {
        return "secondary".equals(role);
    }

    public boolean isVeto() {
        return "veto".equals(role);
    }
}