package dev.automata.automata.repository;

import dev.automata.automata.model.Automation;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AutomationRepository extends MongoRepository<Automation, String> {

    List<Automation> findByIsEnabledTrue();
}
