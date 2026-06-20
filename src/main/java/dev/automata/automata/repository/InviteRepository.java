package dev.automata.automata.repository;

import dev.automata.automata.model.Invite;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;


public interface InviteRepository extends MongoRepository<Invite, String> {
    Optional<Invite> findByTokenAndUsedIsFalse(String token);
}
