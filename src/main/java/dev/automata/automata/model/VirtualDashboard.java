package dev.automata.automata.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;
import java.util.List;


@Document(collection = "virtualDashboard")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class VirtualDashboard {

    @Id
    private String id;
    private String name;
    private Date lastModified;
    private String screen;
    private int height;
    private int width;

    private List<Vids> vids;


}


