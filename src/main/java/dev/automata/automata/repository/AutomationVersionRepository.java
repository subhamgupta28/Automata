package dev.automata.automata.repository;

import dev.automata.automata.model.AutomationVersion;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface AutomationVersionRepository extends MongoRepository<AutomationVersion, String> {
    long countByAutomationId(String automationId);

    List<AutomationVersion> findByAutomationIdOrderByVersionAsc(String automationId);

    List<AutomationVersion> findByAutomationIdOrderByVersionDesc(String automationId);

    Optional<AutomationVersion> findTopByAutomationIdOrderByVersionDesc(String id);

    void deleteByAutomationId(String id);
}
