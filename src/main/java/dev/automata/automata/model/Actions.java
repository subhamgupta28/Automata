package dev.automata.automata.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "action")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class Actions {
    @Id
    private String id;
    private String producerDeviceId;
    private String consumerDeviceId;

    private String producerKey;// range
    private String consumerKey;// pwm1

    private String defaultValue; // 1st arg for condition e.g. 400

//    private String producerValue; // 300
    private String producerValueDataType; // int

    private String valuePositiveC;// 100
    private String valueNegativeC;// 800

    private String condition;// >, <, =, != e.g. 400 > 300 is false so valueNegative (800) is sent
    private String displayName;// action for turning on light when motion detected
    private LocalDateTime triggerTime;

}
