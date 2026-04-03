package dev.automata.automata.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document(collection = "automations")
@CompoundIndexes({
        @CompoundIndex(name = "idx_trigger_device_enabled",
                def = "{'trigger.deviceId': 1, 'isEnabled': 1}"),
})
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
    private Boolean isEnabled;
    private Boolean isActive;
    private Long snoozeTime;
    private String triggerDeviceType;


    private List<String> targetDeviceIds;

    @JsonProperty("trigger")
    private Trigger trigger;
    @JsonProperty("actions")
    private List<Action> actions;
    @JsonProperty("conditions")
    private List<Condition> conditions;

    @JsonProperty("operators")
    private List<Operator> operators;


    public List<String> getTargetDeviceIds() {
        return actions.stream()
                .map(Action::getDeviceId)
                .distinct()
                .toList();
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @ToString
    public static class Trigger {
        private String deviceId;
        private String type; // state, time, etc.
        private String value; // 300
        private String key; //range
        private List<String> keys; //range
        private String name;
        private int priority;
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
        private Boolean isEnabled;
        private Boolean revert = false;
        private String conditionGroup;
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
        private String time;
        private String triggerKey;
        private Boolean isExact; // true
        private String scheduleType;  // "at" | "range"
        private String fromTime;
        private String toTime;
        private List<String> days;
        // Getters and setters
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @ToString
    public static class Operator {
        private String type;
        private String logicType;
    }

}

