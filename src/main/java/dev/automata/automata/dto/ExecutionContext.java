package dev.automata.automata.dto;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ExecutionContext {
    private final Map<String, NodeResult> nodeResults = new HashMap<>();

    public void put(NodeResult result) {
        nodeResults.put(result.getNodeId(), result);
    }

    public NodeResult get(String nodeId) {
        return nodeResults.get(nodeId);
    }

    public Set<String> getFalseNodes() {
        return nodeResults.values().stream()
                .filter(n -> !n.isValue())
                .map(NodeResult::getNodeId)
                .collect(Collectors.toSet());
    }

    public Set<String> getTrueNodes() {
        return nodeResults.values().stream()
                .filter(NodeResult::isTrue)
                .map(NodeResult::getNodeId)
                .collect(Collectors.toSet());
    }

    public Map<String, NodeResult> getAll() {
        return nodeResults;
    }
}
