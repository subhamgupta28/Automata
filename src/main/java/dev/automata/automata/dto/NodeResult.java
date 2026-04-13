package dev.automata.automata.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@Data
public class NodeResult {
    private String nodeId;
    private boolean value;
    private Set<String> contributors = new HashSet<>();

    public NodeResult(String nodeId, boolean value) {
        this.nodeId = nodeId;
        this.value = value;
        if (value) contributors.add(nodeId);
    }

    public boolean isTrue() {
        return value;
    }
    
    public void merge(NodeResult other) {
        if (other != null && other.value) {
            this.contributors.addAll(other.contributors);
        }
    }
}
