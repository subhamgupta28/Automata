package dev.automata.automata.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Document(collection = "energyStat")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class EnergyStat {

    @Id
    private String id;
    @Indexed
    private String deviceId;
    private Long timestamp;
    @Indexed
    private Date updateDate;

    private String status;
    private double totalWh;
    private double peakWh;
    private double lowestWh;

    private double chargeTotalWh;
    private double chargePeakWh;
    private double chargeLowestWh;

    private double percent;

    private double totalWhTrend;
    private double peakWhTrend;
    private double lowestWhTrend;
    private double chargeTotalWhTrend;
    private double chargePeakWhTrend;
    private double chargeLowestWhTrend;
    private double percentTrend;
}
