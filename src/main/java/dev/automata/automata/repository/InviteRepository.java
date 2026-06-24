package dev.automata.automata.repository;

import dev.automata.automata.model.Invite;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;


public interface InviteRepository extends MongoRepository<Invite, String> {
    Optional<Invite> findByTokenAndUsedIsFalse(String token);

    List<Invite> findAllByHomeIdAndUsedIsFalseAndExpiresAtAfter(String homeId, Instant now);
}
