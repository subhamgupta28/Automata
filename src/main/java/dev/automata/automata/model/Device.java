package dev.automata.automata.model;


import lombok.*;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document(collection = "device")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
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
    private List<Attribute> attributes;
}

/*
* This data will be saved on the device it's representing
*
* */