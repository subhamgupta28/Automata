package dev.automata.automata.repository;

import dev.automata.automata.model.WiFiDetails;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface WiFiDetailsRepository extends MongoRepository<WiFiDetails, String> {
}
