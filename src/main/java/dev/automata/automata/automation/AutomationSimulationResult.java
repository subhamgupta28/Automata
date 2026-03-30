package dev.automata.automata.automation;

import dev.automata.automata.model.AutomationLog;

import lombok.Builder;
import lombok.Data;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * LOW PRIORITY: DTO for automation simulation results
 */
@Data
@Builder
public class AutomationSimulationResult {
    private String automationId;
    private String automationName;
    private Map<String, Object> testPayload;
    private Date timestamp;

    private boolean success;
    private String error;

    private boolean wouldTrigger;
    private List<AutomationLog.ConditionResult> conditionResults;

    private List<AutomationSimulationService.SimulatedAction> simulatedActions;
    private int actionCount;

    private List<String> affectedDeviceIds;

    // Metadata
    private String operatorLogic;
    private String triggerType;
}
