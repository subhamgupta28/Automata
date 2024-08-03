package dev.automata.automata.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class Attribute {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long deviceId;
    private String value;
    private String key;
    private String units;
    private String valueDataType;
    private Type type;
    private Long timestamp;


}

enum Type {
    INFO, CONTROL
}
