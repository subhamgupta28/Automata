package dev.automata.automata.automation_engine.guard;

import dev.automata.automata.automation_engine.CoalitionGuard;
import dev.automata.automata.automation_engine.ExecutionPlan;
import dev.automata.automata.automation_engine.TriggerCoalition;
import dev.automata.automata.automation_engine.TriggerMember;
import dev.automata.automata.dto.AutomationRuntimeState;
import dev.automata.automata.model.AutomationLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Slf4j
@Component
@Order(3)
@RequiredArgsConstructor
public class CoalitionPreGuard implements PreExecutionGuard {

    private final CoalitionGuard coalitionGuard;

    @Override
    public GuardResult check(String automationId, ExecutionPlan plan, AutomationRuntimeState state,
                             String firingDeviceId, long nowMs) {
        if (!plan.hasCoalition() || firingDeviceId == null) return GuardResult.proceed(state);

        AutomationRuntimeState stateWithMember = state.withNextVersion();
        stateWithMember.recordMemberFired(firingDeviceId, nowMs);

        CoalitionGuard.CoalitionResult result = coalitionGuard.evaluate(
                plan.getTriggerCoalition(), firingDeviceId, stateWithMember, nowMs);

        log.debug("🤝 [{}] Coalition: {} — {}", plan.getAutomationName(),
                result.status(), result.reason());

        if (!result.shouldProceed()) {
            return GuardResult.skip("Coalition " + result.status() + ": " + result.reason(),
                    AutomationLog.LogStatus.NOT_MET, stateWithMember);
        }

        if (plan.getTriggerCoalition().getMode() == TriggerCoalition.CoalitionMode.SEQUENCE) {
            handleSequenceProgress(plan.getTriggerCoalition(), firingDeviceId, stateWithMember,
                    result.status(), nowMs);
        }

        return GuardResult.proceed(stateWithMember);
    }

    private void handleSequenceProgress(TriggerCoalition coalition, String firingDeviceId,
                                        AutomationRuntimeState state,
                                        CoalitionGuard.CoalitionStatus status, long nowMs) {
        if (status == CoalitionGuard.CoalitionStatus.SATISFIED) {
            state.setSequenceProgress(0);
        } else if (status == CoalitionGuard.CoalitionStatus.NOT_YET) {
            List<TriggerMember> ordered = coalition.getNonVetoMembers().stream()
                    .sorted(Comparator.comparingInt(TriggerMember::getSequenceIndex))
                    .toList();
            int progress = state.getSequenceProgress();
            if (progress < ordered.size()
                    && ordered.get(progress).getDeviceId().equals(firingDeviceId)) {
                state.setSequenceProgress(progress + 1);
            }
        }
    }
}