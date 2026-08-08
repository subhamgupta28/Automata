package dev.automata.automata.repository;

import dev.automata.automata.model.AutomationStateSnapshot;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AutomationStateSnapshotRepository extends MongoRepository<AutomationStateSnapshot, String> {
}
