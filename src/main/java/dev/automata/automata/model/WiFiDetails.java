package dev.automata.automata.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WiFiDetails {

    @Id
    private String id;
    private String ssid;
    private String password;
    private String type;
}
