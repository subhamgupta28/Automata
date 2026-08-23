package dev.automata.automata.automation_engine.evaluator;

import dev.automata.automata.automation_engine.EvalResult;
import dev.automata.automata.automation_engine.ExecutionPlan;
import dev.automata.automata.automation_engine.enums.EvalOutcome;
import dev.automata.automata.dto.AutomationRuntimeState;
import dev.automata.automata.model.AutomationLog;

public interface OutcomeHandler {
    EvalOutcome outcome();

    boolean hasChanges();

    /**
     * Same responsibility as one case-block in the old computeNextState() switch.
     */
    void applyStateTransition(AutomationRuntimeState next, EvalResult result, ExecutionPlan plan);

    AutomationLog.LogStatus logStatus();

    /**
     * Same responsibility as one case-block in the old dispatchResult() switch.
     */
    void dispatch(EvalResult result, ExecutionPlan plan, DispatchContext ctx);

    default boolean persistsSnapshot() {
        return false;
    }

    // Mongo-snapshot outcome == TRIGGERED||BRANCH_TRIGGERED||C1_NEGATIVE check.
    default boolean armsScheduleKeys() {
        return false;
    }
}
