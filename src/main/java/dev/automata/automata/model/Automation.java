package dev.automata.automata.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document(collection = "automations")
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
    public static class Trigger {

        private String deviceId;
        private String type; // state, time
        private String value; // 300
        private String key; //range

    }

    @Getter
    @Setter
    public static class Action {
        private String key; //lights
        private String deviceId; //---
        private String data; //255

        // Getters and setters
    }

    @Getter
    @Setter
    public static class Condition {
        private String condition;// numeric
        private String valueType;// int
        private String above; // 200
        private String below; // 300
        private String value;

        // Getters and setters
    }



}

