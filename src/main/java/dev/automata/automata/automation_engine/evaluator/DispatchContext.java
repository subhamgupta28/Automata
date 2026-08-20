package dev.automata.automata.automation_engine.evaluator;

import dev.automata.automata.automation_engine.ActionDispatcher;
import dev.automata.automata.automation_engine.AutomationStateStore;
import dev.automata.automata.automation_engine.PlanCache;
import dev.automata.automata.service.NotificationService;

import java.util.Map;

/**
 * Bundles what an OutcomeHandler's dispatch() needs, built fresh per call by the orchestrator.
 */
public record DispatchContext(
        ActionDispatcher dispatcher,
        AutomationStateStore stateStore,
        PlanCache planCache,
        NotificationService notificationService,
        String automationId,
        String name,
        String traceId,
        String homeId,
        Map<String, Object> payload,
        String user,
        Runnable publishLog
) {
}
