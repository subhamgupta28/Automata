package dev.automata.automata.automation_engine.evaluator;

import dev.automata.automata.automation_engine.EvalResult;
import dev.automata.automata.automation_engine.ExecutionPlan;
import dev.automata.automata.automation_engine.enums.EvalOutcome;
import dev.automata.automata.dto.AutomationRuntimeState;
import dev.automata.automata.model.AutomationLog;
import org.springframework.stereotype.Component;

@Component
public class FallbackHandler implements OutcomeHandler {
    @Override
    public EvalOutcome outcome() {
        return EvalOutcome.FALLBACK;
    }

    @Override
    public boolean hasChanges() {
        return true;
    }

    @Override
    public void applyStateTransition(AutomationRuntimeState next, EvalResult result, ExecutionPlan plan) { /* no-op */ }

    @Override
    public AutomationLog.LogStatus logStatus() {
        return AutomationLog.LogStatus.TRIGGERED;
    }

    @Override
    public void dispatch(EvalResult result, ExecutionPlan plan, DispatchContext ctx) {
        ctx.dispatcher().dispatch(DispatchSupport.resolveFinalActions(result.getActionsToFire()),
                        ctx.payload(), ctx.user(), ctx.automationId(), ctx.name(), ctx.traceId(), ctx.homeId())
                .thenRun(() -> ctx.publishLog().run());
    }
}
