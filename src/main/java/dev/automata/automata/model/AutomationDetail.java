package dev.automata.automata.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.Map;

@Document(collection = "automationDetail")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class AutomationDetail {

    @Id
    private String id;
    private List<Map<String, Object>> nodes;
    private List<Map<String, Object>> edges;
    private Map<String, Object> viewport;
}
