package dev.automata.automata.repository;

import dev.automata.automata.model.AutomationDetail;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AutomationDetailRepository extends MongoRepository<AutomationDetail, String> {
}
