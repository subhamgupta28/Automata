package dev.automata.automata.repository;


import dev.automata.automata.model.MasterOption;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface MasterOptionRepository extends MongoRepository<MasterOption, String> {
}
