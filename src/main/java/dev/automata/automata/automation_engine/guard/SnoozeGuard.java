package dev.automata.automata.automation_engine.guard;

import dev.automata.automata.automation_engine.AutomationStateStore;
import dev.automata.automata.automation_engine.ExecutionPlan;
import dev.automata.automata.dto.AutomationRuntimeState;
import dev.automata.automata.model.AutomationLog;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Order(1)
@RequiredArgsConstructor
public class SnoozeGuard implements PreExecutionGuard {

    private final AutomationStateStore stateStore;

    @Override
    public GuardResult check(String automationId, ExecutionPlan plan, AutomationRuntimeState state,
                             String firingDeviceId, long nowMs) {
        if (!stateStore.isSnoozed(automationId)) return GuardResult.proceed(state);

        long rem = Optional.ofNullable(stateStore.snoozeTTL(automationId)).orElse(0L);
        return GuardResult.skip("Snoozed — " + rem / 60 + "min remaining",
                AutomationLog.LogStatus.SUPPRESSED, state);
    }
}