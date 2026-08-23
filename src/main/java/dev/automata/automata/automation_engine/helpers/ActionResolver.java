package dev.automata.automata.automation_engine.helpers;

import dev.automata.automata.automation_engine.ExecutionPlan;
import dev.automata.automata.automation_engine.dto.ActionKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ActionResolver {

    /**
     * First occurrence per (deviceId,key) wins; logs a warning on data conflicts.
     */
    public List<ExecutionPlan.CompiledAction> resolve(List<ExecutionPlan.CompiledAction> actions) {
        if (actions == null || actions.size() <= 1) return actions == null ? List.of() : actions;
        Map<ActionKey, ExecutionPlan.CompiledAction> byKey = new LinkedHashMap<>();
        for (var a : actions) {
            var key = ActionKey.of(a);
            var prior = byKey.putIfAbsent(key, a);
            if (prior != null && !Objects.equals(prior.getData(), a.getData())) {
                log.warn("⚠️ Action conflict on device='{}' key='{}': keeping '{}' from node '{}', dropping '{}' from node '{}'",
                        a.getDeviceId(), a.getKey(), prior.getData(), prior.getNodeId(), a.getData(), a.getNodeId());
            }
        }
        return new ArrayList<>(byKey.values());
    }

    /**
     * Same policy, but exposes whether a value was actually seen before (used by dedupe-with-full-identity call sites).
     */
    public List<ExecutionPlan.CompiledAction> resolveExact(List<ExecutionPlan.CompiledAction> actions) {
        Set<String> seen = new LinkedHashSet<>();
        return actions.stream()
                .filter(a -> seen.add(a.getDeviceId() + "|" + a.getKey() + "|" + a.getData()))
                .collect(Collectors.toList());
    }
}
