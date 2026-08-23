package dev.automata.automata.automation_engine.dto;

import dev.automata.automata.automation_engine.ExecutionPlan;

public record ActionKey(String deviceId, String key) {
    public static ActionKey of(ExecutionPlan.CompiledAction a) {
        return new ActionKey(a.getDeviceId(), a.getKey());
    }
}
