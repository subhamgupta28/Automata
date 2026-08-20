package dev.automata.automata.automation_engine.evaluator;

import dev.automata.automata.automation_engine.enums.EvalOutcome;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class OutcomeHandlerRegistry {
    private final Map<EvalOutcome, OutcomeHandler> byOutcome;

    // Spring autowires every OutcomeHandler @Component into this list — nothing to register by hand.
    public OutcomeHandlerRegistry(List<OutcomeHandler> handlers) {
        this.byOutcome = handlers.stream().collect(Collectors.toMap(OutcomeHandler::outcome, h -> h));
    }

    public OutcomeHandler get(EvalOutcome outcome) {
        OutcomeHandler handler = byOutcome.get(outcome);
        if (handler == null) throw new IllegalStateException("No OutcomeHandler registered for outcome " + outcome);
        return handler;
    }
}
