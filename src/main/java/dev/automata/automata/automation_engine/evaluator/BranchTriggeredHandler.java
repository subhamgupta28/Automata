package dev.automata.automata.automation_engine.evaluator;

import dev.automata.automata.automation_engine.EvalResult;
import dev.automata.automata.automation_engine.ExecutionPlan;
import dev.automata.automata.automation_engine.enums.EvalOutcome;
import dev.automata.automata.automation_engine.enums.NodeState;
import dev.automata.automata.dto.AutomationRuntimeState;
import dev.automata.automata.model.AutomationLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class BranchTriggeredHandler implements OutcomeHandler {

    @Override
    public EvalOutcome outcome() {
        return EvalOutcome.BRANCH_TRIGGERED;
    }

    @Override
    public boolean hasChanges() {
        return true;
    }

    @Override
    public void applyStateTransition(AutomationRuntimeState next, EvalResult result, ExecutionPlan plan) {
        // BUG 4 fix, verbatim: do NOT reset topLevelState — it's already ACTIVE, or becoming so.
        next.setTopLevelState(NodeState.ACTIVE);
        next.setLastExecutionTime(new Date());
        NodeStateUtils.applyPerNodeActiveFlags(next, plan, result.getConditionResults());
    }

    @Override
    public AutomationLog.LogStatus logStatus() {
        return AutomationLog.LogStatus.TRIGGERED;
    }

    @Override
    public boolean armsScheduleKeys() {
        return true;
    }

    @Override
    public boolean persistsSnapshot() {
        return true;
    }

    @Override
    public void dispatch(EvalResult result, ExecutionPlan plan, DispatchContext ctx) {
        List<ExecutionPlan.CompiledAction> actions =
                result.getActionsToFire() != null && !result.getActionsToFire().isEmpty()
                        ? result.getActionsToFire() : List.of();

        if (actions.isEmpty()) {
            log.warn("⚠️ [{}] BRANCH_TRIGGERED but actionsToFire is empty — nothing to dispatch", ctx.name());
            ctx.publishLog().run();
            return;
        }

        DispatchSupport.armDurationWindows(result, ctx);
        DispatchSupport.armResendThrottles(result, plan, ctx);

        ctx.dispatcher().dispatch(DispatchSupport.resolveFinalActions(actions), ctx.payload(), ctx.user(),
                        ctx.automationId(), ctx.name(), ctx.traceId(), ctx.homeId())
                .thenRun(() -> {
                    log.info("🌿 [{}] Branch triggered — {} action(s) dispatched", ctx.name(), actions.size());
                    ctx.dispatcher().notifyTriggered(ctx.name(), ctx.homeId(), ctx.automationId(),
                            TriggerDescriptionBuilder.build(result, plan));
                    ctx.publishLog().run();
                });
    }
}
