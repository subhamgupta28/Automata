package dev.automata.automata.model;


import lombok.*;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Document(collection = "device")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class Device {
    @Id
    private String id;
    private String name;
    private String type;
    private String category;
    private String host;
    private Long updateInterval;
    private Status status;
    private Boolean reboot;
    private Boolean sleep;
    private String accessUrl;
    private String macAddr;
    private Date lastOnline;
    private Date lastRegistered;
    private Map<String, Object> lastData;
    private boolean showCharts;
    private boolean showInDashboard;
    private boolean analytics;
    private double x;
    private double y;
    private List<Attribute> attributes = new ArrayList<>();
}

/*
* This data will be saved on the device it's representing
*
* */