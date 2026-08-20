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
public class TriggeredHandler implements OutcomeHandler {

    @Override
    public EvalOutcome outcome() {
        return EvalOutcome.TRIGGERED;
    }

    @Override
    public boolean hasChanges() {
        return true;
    }

    @Override
    public void applyStateTransition(AutomationRuntimeState next, EvalResult result, ExecutionPlan plan) {
        next.setTopLevelState(NodeState.ACTIVE);
        next.setLastExecutionTime(new Date());
        NodeStateUtils.applyPerNodeActiveFlags(next, plan, result.getConditionResults());
    }

    @Override
    public AutomationLog.LogStatus logStatus() {
        return AutomationLog.LogStatus.TRIGGERED;
    }

    @Override
    public void dispatch(EvalResult result, ExecutionPlan plan, DispatchContext ctx) {
        List<ExecutionPlan.CompiledAction> actions =
                result.getActionsToFire() != null && !result.getActionsToFire().isEmpty()
                        ? result.getActionsToFire()
                        : (plan.getTopLevelPositiveActions() != null
                           ? plan.getTopLevelPositiveActions() : List.of());

        DispatchSupport.armDurationWindows(result, ctx);
        DispatchSupport.armResendThrottles(result, plan, ctx);

        ctx.dispatcher().dispatch(DispatchSupport.resolveFinalActions(actions), ctx.payload(), ctx.user(),
                        ctx.automationId(), ctx.name(), ctx.traceId(), ctx.homeId())
                .thenRun(() -> {
                    log.info("🚀 [{}] Triggered", ctx.name());
                    ctx.dispatcher().notifyTriggered(ctx.name(), ctx.homeId(), ctx.automationId(),
                            TriggerDescriptionBuilder.build(result, plan));
                    ctx.publishLog().run();
                });
    }
}
