package dev.automata.automata.automation_engine.guard;

import dev.automata.automata.automation_engine.ExecutionPlan;
import dev.automata.automata.dto.AutomationRuntimeState;
import dev.automata.automata.model.AutomationLog;

public interface PreExecutionGuard {
    GuardResult check(String automationId, ExecutionPlan plan, AutomationRuntimeState state,
                      String firingDeviceId, long nowMs);

    record GuardResult(boolean proceed, String skipReason,
                       AutomationLog.LogStatus status,
                       AutomationRuntimeState updatedState) {

        public static GuardResult proceed(AutomationRuntimeState state) {
            return new GuardResult(true, null, null, state);
        }

        public static GuardResult skip(String reason, AutomationLog.LogStatus status,
                                       AutomationRuntimeState state) {
            return new GuardResult(false, reason, status, state);
        }
    }
}