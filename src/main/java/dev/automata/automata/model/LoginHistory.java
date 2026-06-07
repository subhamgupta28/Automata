package dev.automata.automata.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "login_history")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class LoginHistory {

    @Id
    private String id;
    private String userId;
    private String email;
    private String firstName;
    private String lastName;
    private String ipAddress;
    private String userAgent;
    private String deviceInfo;
    private String location; // Can be populated from IP geolocation service
    private String country;
    private String city;
    private String browser;
    private String operatingSystem;
    private boolean success;
    private String failureReason; // If login failed, store reason
    private Instant loginTime;
    private Instant logoutTime; // Optional: track logout
    private long sessionDurationSeconds; // Duration of session
}
