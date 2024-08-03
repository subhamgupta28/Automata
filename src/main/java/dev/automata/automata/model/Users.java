package dev.automata.automata.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

import java.util.Date;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Users {

    @Id
    private int id;
    private String username;
    private String password;
    private String email;
    private Date timestamp;
    private Role role;
}

enum Role {
    USER, ADMIN
}
