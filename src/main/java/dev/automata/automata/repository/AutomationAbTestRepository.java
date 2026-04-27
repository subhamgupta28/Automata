package dev.automata.automata.repository;

import dev.automata.automata.model.AutomationAbTest;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface AutomationAbTestRepository extends MongoRepository<AutomationAbTest, String> {
    List<AutomationAbTest> findByStatus(AutomationAbTest.AbTestStatus abTestStatus);

    Optional<AutomationAbTest> findByVariantAIdAndStatus(String variantAId, AutomationAbTest.AbTestStatus abTestStatus);
}
