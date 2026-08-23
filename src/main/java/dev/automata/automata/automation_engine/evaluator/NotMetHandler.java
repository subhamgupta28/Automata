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
public class NotMetHandler implements OutcomeHandler {
    @Override
    public EvalOutcome outcome() {
        return EvalOutcome.NOT_MET;
    }

    @Override
    public boolean hasChanges() {
        return false;
    }

    @Override
    public void applyStateTransition(AutomationRuntimeState next, EvalResult result, ExecutionPlan plan) {
        /* no-op */
    }

    @Override
    public AutomationLog.LogStatus logStatus() {
        return AutomationLog.LogStatus.NOT_MET;
    }

    @Override
    public void dispatch(EvalResult result, ExecutionPlan plan, DispatchContext ctx) {
        log.debug("[{}] NotMetHandler: result={}", plan.getAutomationName(), result.getOutcome());
        ctx.publishLog().run();
    }
}

