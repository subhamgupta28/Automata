package dev.automata.automata.model;


import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Map;

@Document(collection = "data")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString

public class Data {
    @Id
    private String id;
    private String deviceId;
    private String dateTime;
    private Map<String, Object> data;
    private Long timestamp;
    private Date updateDate;
}
