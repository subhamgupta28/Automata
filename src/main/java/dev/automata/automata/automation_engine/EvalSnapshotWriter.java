package dev.automata.automata.automation_engine;

import dev.automata.automata.dto.AutomationRuntimeState;
import dev.automata.automata.dto.ConditionMemory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class EvalSnapshotWriter {

    private final AutomationEvaluator evaluator; // for summarizeMemory()
    private final AutomationStateStore stateStore;

    public void write(String automationId,
                      ExecutionPlan plan,
                      EvalResult result,
                      AutomationRuntimeState currentState) {
        try {
            AutomationRuntimeState.EvalSnapshot snapshot = new AutomationRuntimeState.EvalSnapshot();
            snapshot.setOutcome(result.getOutcome().name());
            snapshot.setTraceId(result.getTraceId());
            snapshot.setEvaluatedAt(result.getEvaluatedAt());
            snapshot.setC1True(result.isC1True());
            snapshot.setAnyWasActive(result.isAnyWasActive());
            snapshot.setReason(result.getReason());
            snapshot.setEvalDurationMs(result.getEvalDurationMs());
            snapshot.setConditionResults(result.getConditionResults());

            snapshot.setNodeStates(new HashMap<>(currentState.getNodeStates()));
            snapshot.setCoalitionLastFired(new HashMap<>(currentState.getTriggerMemberLastFired()));
            snapshot.setSequenceProgress(currentState.getSequenceProgress());

            if (plan.getConditionTree() != null && result.getMemoryUpdates() != null) {
                Map<String, String> summaries = new LinkedHashMap<>();
                for (ExecutionPlan.CompiledConditionNode node : plan.getConditionTree()) {
                    if (node.hasMemoryPolicy()) {
                        ConditionMemory mem = result.getMemoryUpdates().get(node.getNodeId());
                        if (mem != null) {
                            String s = evaluator.summarizeMemory(node.getMemoryPolicy(), mem);
                            summaries.put(node.getNodeId(), s);
                        }
                    }
                }
                snapshot.setConditionMemorySummaries(summaries);
            }

//            AutomationRuntimeState latest = stateStore.read(automationId);
            currentState.setLastEvalSnapshot(snapshot);
            stateStore.forceWrite(automationId, currentState);
        } catch (Exception e) {
            log.warn("⚠️ Failed to write eval snapshot for '{}': {}", automationId, e.getMessage());
        }
    }
}
