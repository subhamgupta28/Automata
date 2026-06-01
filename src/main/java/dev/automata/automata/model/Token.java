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
public class Token {

    @Id
    public Integer id;

    public String token;

    public boolean revoked;

    public boolean expired;

    public Users user;
}
