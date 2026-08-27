package dev.automata.automata.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "virtualDeviceAccess")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class VirtualDeviceAccess {
    @Id
    private String id;
    @Indexed
    private String vid;
    @Indexed
    private String userId;
    private Instant grantedAt;
    private String password;
    private boolean locked = true;
    private boolean permanentUnlock = false;   // if true, unlocking never expires until manually re-locked
    private int unlockDurationMinutes = 30;    // used when permanentUnlock = false
    private Instant unlockedUntil;             // null = no active unlock session
    @Indexed
    @LastModifiedDate
    private Instant updateDate;
}
