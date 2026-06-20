package dev.automata.automata.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@CompoundIndexes({
        @CompoundIndex(name = "userId_homeId", def = "{'userId': 1, 'homeId': 1}", unique = true)
})
@Document(collection = "home_access")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeAccess {

    @Id
    private String id;

    @Indexed
    private String userId;
    @Indexed
    private String homeId;

    private HomeRole role;           // OWNER / ADMIN / MEMBER

    private Instant grantedAt;
    private String grantedByUserId;  // audit trail
}
