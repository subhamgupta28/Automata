package dev.automata.automata.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document(collection = "automations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Automation {
    @Id
    private String id;
    private String name;

    @JsonProperty("trigger")
    private Trigger trigger;
    @JsonProperty("actions")
    private List<Action> actions;
    @JsonProperty("conditions")
    private List<Condition> conditions;
    private int x;
    private int y;


    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @ToString
    public static class Trigger {

        private String deviceId;
        private String type; // state, time
        private String value; // 300
        private String key; //range
        private String name;
        private int x;
        private int y;
    }

    @Getter
    @Setter
    @ToString
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Action {
        private String key; //lights
        private String deviceId; //---
        private String data; //255
        private String name;
        private int x;
        private int y;
        // Getters and setters
    }

    @Getter
    @Setter
    @ToString
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Condition {
        private String condition;// numeric
        private String valueType;// int
        private String above; // 200
        private String below; // 300
        private String value;
        private Boolean isExact; // true
        private int x;
        private int y;
        // Getters and setters
    }



}

