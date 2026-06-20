package dev.automata.automata.repository;

import dev.automata.automata.model.Home;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface HomeRepository extends MongoRepository<Home, String> {
}
