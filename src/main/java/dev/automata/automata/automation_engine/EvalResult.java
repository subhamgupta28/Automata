package dev.automata.automata.automation_engine;

import dev.automata.automata.automation_engine.dto.GraceUpdate;
import dev.automata.automata.automation_engine.enums.EvalOutcome;
import dev.automata.automata.dto.ConditionMemory;
import lombok.Builder;
import lombok.Value;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Builder(toBuilder = true)
@Value
public class EvalResult {
    String automationId;
    Date evaluatedAt;
    boolean c1True;
    EvalOutcome outcome;
    String reason;
    Map<String, Boolean> conditionResults;
    List<ExecutionPlan.CompiledAction> actionsToFire;
    String nextTopLevelState;
    Date triggeredAt;
    boolean anyWasActive;
    String traceId;
    Long evalDurationMs;
    boolean shouldArmIntervalCooldown;
    String intervalCooldownNodeId;
    long intervalCooldownTtlSeconds;
    boolean shouldWriteDailySolarKey;
    boolean shouldWriteDailyFireKey;
    Map<String, ConditionMemory> memoryUpdates;
    Map<String, GraceUpdate> graceUpdates;

    /**
     * BUG 2 fix: nodeIds of interval-scheduled conditions (durationMinutes>0)
     * that evaluated true THIS tick. The orchestrator arms
     * stateStore.setRunningKey() for each of these after a successful
     * positive-action dispatch.
     */
    Set<String> intervalNodesToArm;

    public boolean hasActions() {
        return actionsToFire != null && !actionsToFire.isEmpty();
    }

}
