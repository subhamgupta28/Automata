package dev.automata.automata.model;

import dev.automata.automata.automation_engine.enums.NodeState;
import dev.automata.automata.dto.AutomationRuntimeState;
import dev.automata.automata.dto.ConditionMemory;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Document(collection = "automation_state_snapshot")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class AutomationStateSnapshot {

    @Id
    private String id;

    private long version = 0;
    private NodeState topLevelState = NodeState.IDLE;
    private Map<String, NodeState> nodeStates = new HashMap<>();
    private Map<String, ConditionMemory> conditionMemories = new HashMap<>();
    private Map<String, Long> triggerMemberLastFired = new HashMap<>();
    private int sequenceProgress = 0;
    private AutomationRuntimeState.EvalSnapshot lastEvalSnapshot;
    private Date lastExecutionTime;
    @Indexed
    @LastModifiedDate
    private Instant savedAt;

    public static AutomationStateSnapshot from(String automationId, AutomationRuntimeState nextState, Instant now) {
        AutomationStateSnapshot state = new AutomationStateSnapshot();
        state.setId(automationId);
        state.setTopLevelState(nextState.getTopLevelState());
        state.setNodeStates(nextState.getNodeStates());
        state.setConditionMemories(nextState.getConditionMemories());
        state.setTriggerMemberLastFired(nextState.getTriggerMemberLastFired());
        state.setSequenceProgress(nextState.getSequenceProgress());
        state.setLastEvalSnapshot(nextState.getLastEvalSnapshot());
        state.setLastExecutionTime(nextState.getLastExecutionTime());
        state.setSavedAt(now);
        state.setVersion(nextState.getVersion());
        return state;
    }

    public AutomationRuntimeState toRuntimeState() {
        AutomationRuntimeState state = new AutomationRuntimeState();
        state.setVersion(this.version);          // ← ADD THIS
        state.setTopLevelState(topLevelState);
        state.setNodeStates(nodeStates != null ? new HashMap<>(nodeStates) : new HashMap<>());
        state.setConditionMemories(conditionMemories != null ? new HashMap<>(conditionMemories) : new HashMap<>());
        state.setTriggerMemberLastFired(triggerMemberLastFired != null ? new HashMap<>(triggerMemberLastFired) : new HashMap<>());
        state.setSequenceProgress(sequenceProgress);
        state.setLastEvalSnapshot(lastEvalSnapshot);
        state.setLastExecutionTime(lastExecutionTime);
        state.setSavedAt(savedAt);
        return state;
    }
}
