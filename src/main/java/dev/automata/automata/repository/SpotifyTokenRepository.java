package dev.automata.automata.repository;

import dev.automata.automata.model.SpotifyToken;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SpotifyTokenRepository extends MongoRepository<SpotifyToken, String> {
}
