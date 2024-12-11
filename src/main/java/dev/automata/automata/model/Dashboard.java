package dev.automata.automata.model;


import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "dashboard")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class Dashboard {

    @Id
    private String id;

    private String deviceId;
    private double x;
    private double y;
    private boolean showCharts;
    private boolean showInDashboard;
}
