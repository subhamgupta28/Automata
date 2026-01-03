package dev.automata.automata.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Document(collection = "virtualDevice")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class VirtualDevice {

    @Id
    private String id;
    private String name;
    private double x;
    private double y;
    private double width;
    private double height;
    private Date lastModified;
    private String tag;

    private List<String> deviceIds;
    private Map<String, List<Attribute>> attributes;
}
