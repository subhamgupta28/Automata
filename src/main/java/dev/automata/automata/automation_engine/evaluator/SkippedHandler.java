package dev.automata.automata.automation_engine.evaluator;

import dev.automata.automata.automation_engine.EvalResult;
import dev.automata.automata.automation_engine.ExecutionPlan;
import dev.automata.automata.automation_engine.enums.EvalOutcome;
import dev.automata.automata.dto.AutomationRuntimeState;
import dev.automata.automata.model.AutomationLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SkippedHandler implements OutcomeHandler {
    @Override
    public EvalOutcome outcome() {
        return EvalOutcome.SKIPPED;
    }

    @Override
    public boolean hasChanges() {
        return false;
    }

    @Override
    public void applyStateTransition(AutomationRuntimeState next, EvalResult result, ExecutionPlan plan) {
        /* no-op — matches the old switch's default case */
    }

    @Override
    public AutomationLog.LogStatus logStatus() {
        return AutomationLog.LogStatus.SKIPPED;
    }

    @Override
    public void dispatch(EvalResult result, ExecutionPlan plan, DispatchContext ctx) {
        log.debug("[{}] SkippedHandler: result={}", plan.getAutomationName(), result.getOutcome());
        ctx.publishLog().run();
    }
}
