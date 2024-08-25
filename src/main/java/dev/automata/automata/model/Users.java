package dev.automata.automata.model;

import lombok.*;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.annotation.Id;
import java.util.Date;

@Document(collection = "users")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Users {

    @Id
    private String id;
    private String username;
    private String password;
    private String email;
    private Date timestamp;
    private Role role;
}

enum Role {
    USER, ADMIN
}
