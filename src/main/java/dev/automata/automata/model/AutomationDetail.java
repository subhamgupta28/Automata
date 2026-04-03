package dev.automata.automata.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

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
    @JsonProperty("edges")
    private List<Edge> edges;
    @JsonProperty("nodes")
    private List<Node> nodes;
    @JsonProperty("viewport")
    private Viewport viewport;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @ToString
    public static class Edge {
        private String source;
        private String sourceHandle;
        private String target;
        private String targetHandle;
        private String type;
        private String id;
        @JsonProperty("data")
        private Data data;

        @Getter
        @Setter
        @AllArgsConstructor
        @NoArgsConstructor
        @ToString
        public static class Data {
            private String color;
        }

    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @ToString
    public static class Node {
        private String id;
        private String type;
        @JsonProperty("position")
        private Position position;
        @JsonProperty("data")
        private Data data;
        @JsonProperty("measured")
        private Measured measured;
        private boolean selected;
        private boolean dragging;

        @Getter
        @Setter
        @AllArgsConstructor
        @NoArgsConstructor
        @ToString
        public static class Position {
            private double x;
            private double y;
        }

        @Getter
        @Setter
        @AllArgsConstructor
        @NoArgsConstructor
        @ToString
        public static class Data {
            @JsonProperty("value")
            private Value value;
            //            @JsonProperty("triggerData")
            private TriggerData triggerData;
            //            @JsonProperty("conditionData")
            private ConditionData conditionData;
            //            @JsonProperty("actionData")
            private ActionData actionData;
            private Operator operators;

            @Getter
            @Setter
            @AllArgsConstructor
            @NoArgsConstructor
            @ToString
            public static class Operator {
                private String type;
                private String logicType;
            }

            @Getter
            @Setter
            @AllArgsConstructor
            @NoArgsConstructor
            @ToString
            public static class Value {
                private boolean isNewNode;
                private String name;
            }

            @Getter
            @Setter
            @AllArgsConstructor
            @NoArgsConstructor
            @ToString
            public static class TriggerData {
                private String deviceId;
                private String type;
                private String key;
                @JsonProperty("keys")
                private List<TriggerKeys> keys;
                private String name;
                private String value;
                private int priority;

                @Getter
                @Setter
                @AllArgsConstructor
                @NoArgsConstructor
                @ToString
                public static class TriggerKeys {
                    private String conditionId;
                    private String value;
                    private String key;
                }
            }

            @Getter
            @Setter
            @AllArgsConstructor
            @NoArgsConstructor
            @ToString
            public static class ConditionData {
                private String condition;
                private String valueType;
                private String below;
                private String above;
                private String triggerKey;
                private String value;
                private Boolean isExact;
                private String time;
                private String scheduleType;
                private String fromTime;
                private String toTime;
                @JsonProperty("days")
                private List<String> days;
            }

            @Getter
            @Setter
            @AllArgsConstructor
            @NoArgsConstructor
            @ToString
            public static class ActionData {
                private String deviceId;
                private String key;
                private String name;
                private String data;
                private Boolean isEnabled;
                private Boolean revert = false;
                private String conditionGroup;
            }
        }

        @Getter
        @Setter
        @AllArgsConstructor
        @NoArgsConstructor
        @ToString
        public static class Measured {
            private int width;
            private int height;
        }
    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    @ToString
    public static class Viewport {
        private double x;
        private double y;
        private double zoom;
    }
}
