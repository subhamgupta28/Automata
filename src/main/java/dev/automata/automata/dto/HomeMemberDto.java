// dto/HomeMemberDto.java
package dev.automata.automata.dto;

import dev.automata.automata.model.HomeRole;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class HomeMemberDto {
    private String userId;
    private String name;
    private String email;
    private HomeRole role;
    private String grantedByUserId;
    private java.time.Instant grantedAt;
}