package dev.automata.automata.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document(collection = "action")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class Automation {
    @Id
    private String id;
    private String name;
    private Trigger trigger;
    private List<Action> actions;
    private List<Condition> conditions;

    @Getter
    @Setter
    @Builder
    @RequiredArgsConstructor
    @NoArgsConstructor
    public static class Trigger {

        private String deviceId;
        private String type; // state, time
        private String value; // 300
        private String key; //range

    }

    @Getter
    @Setter
    @Builder
    @RequiredArgsConstructor
    @NoArgsConstructor
    public static class Action {
        private String key; //lights
        private String deviceId; //---
        private String data; //

        // Getters and setters
    }

    @Getter
    @Setter
    @Builder
    @RequiredArgsConstructor
    @NoArgsConstructor
    public static class Condition {
        private String type;
        private String value;

        // Getters and setters
    }



}

