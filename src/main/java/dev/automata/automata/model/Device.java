package dev.automata.automata.model;


import lombok.*;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

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
    private Long updateInterval;
    private Status status;
    private Boolean reboot;
    private Boolean sleep;
    private String accessUrl;
    private String macAddr;
    private List<Attribute> attributes = new ArrayList<>();
}

/*
* This data will be saved on the device it's representing
*
* */