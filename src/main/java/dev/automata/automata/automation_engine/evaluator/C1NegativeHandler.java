package dev.automata.automata.automation_engine.evaluator;

import dev.automata.automata.automation_engine.EvalResult;
import dev.automata.automata.automation_engine.ExecutionPlan;
import dev.automata.automata.automation_engine.enums.EvalOutcome;
import dev.automata.automata.automation_engine.enums.NodeState;
import dev.automata.automata.dto.AutomationRuntimeState;
import dev.automata.automata.model.AutomationLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class C1NegativeHandler implements OutcomeHandler {

    @Override
    public EvalOutcome outcome() {
        return EvalOutcome.C1_NEGATIVE;
    }

    @Override
    public boolean hasChanges() {
        return true;
    }

    @Override
    public void applyStateTransition(AutomationRuntimeState next, EvalResult result, ExecutionPlan plan) {
        next.setTopLevelState(NodeState.IDLE);
        // Pass 1: nodes walked this tick get their actual result.
        NodeStateUtils.applyPerNodeActiveFlags(next, plan, result.getConditionResults());
        // Pass 2 (BUG 3 fix, verbatim): force-IDLE any stateful node still ACTIVE after
        // pass 1, to stop stray intermediate nodes causing duplicate notifications.
        if (plan.getConditionTree() != null) {
            for (ExecutionPlan.CompiledConditionNode node : plan.getConditionTree()) {
                if (node.isStateful() && next.isNodeActive(node.getNodeId())) {
                    next.setNodeState(node.getNodeId(), NodeState.IDLE);
                }
            }
        }
    }

    @Override
    public AutomationLog.LogStatus logStatus() {
        return AutomationLog.LogStatus.TRIGGER_FALSE;
    }

    @Override
    public void dispatch(EvalResult result, ExecutionPlan plan, DispatchContext ctx) {
        // BUG 1 fix: actionsToFire already includes stranded-descendant negatives,
        // folded in by foldInStrandedNegativeActions() before this is ever called.
        List<ExecutionPlan.CompiledAction> toFire = DispatchSupport.resolveFinalActions(
                result.getActionsToFire() != null
                        ? new ArrayList<>(result.getActionsToFire()) : new ArrayList<>());

        boolean wasFirstTransition = result.isAnyWasActive();

        ctx.dispatcher().dispatch(toFire, ctx.payload(), ctx.user(), ctx.automationId(), ctx.name(), ctx.traceId(), ctx.homeId())
                .thenRun(() -> {
                    log.debug("[{}] — trigger condition lost", ctx.name());
                    if (wasFirstTransition) {
                        ctx.notificationService().sendNotification(
                                ctx.name() + ": Trigger condition is no longer met",
                                "warning", ctx.name(), ctx.homeId(), ctx.automationId());
                    }
                    ctx.publishLog().run();
                });
    }
}
