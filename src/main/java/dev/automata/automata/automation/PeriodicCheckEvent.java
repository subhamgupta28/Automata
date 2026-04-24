package dev.automata.automata.automation;


import dev.automata.automata.model.Automation;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.Map;

/**
 * Fired by the scheduler for each automation that needs a periodic evaluation.
 */
@Getter
public class PeriodicCheckEvent extends ApplicationEvent {

    private final Automation automation;
    private final Map<String, Object> recentData;
    private final String triggerSource;   // ← add this

    public PeriodicCheckEvent(Object source, Automation automation,
                              Map<String, Object> recentData) {
        super(source);
        this.automation = automation;
        this.recentData = recentData;
        this.triggerSource = "system";    // default for 12s poll
    }

    // ← add this overload
    public PeriodicCheckEvent(Object source, Automation automation,
                              Map<String, Object> recentData, String triggerSource) {
        super(source);
        this.automation = automation;
        this.recentData = recentData;
        this.triggerSource = triggerSource;
    }
}
