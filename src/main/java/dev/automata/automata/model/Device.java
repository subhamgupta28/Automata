package dev.automata.automata.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.*;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Device {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String type;
    private Long updateInterval;
    private Status status;
    private Boolean reboot;
    private Boolean sleep;
    private String accessUrl;
}

/*
* This data will be saved on the device it's representing
*
* */