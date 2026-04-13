package dev.automata.automata.repository;

import dev.automata.automata.model.FeatureToggle;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface FeatureToggleRepository extends MongoRepository<FeatureToggle, String> {
    FeatureToggle findByEnvAndFeatureKeyAndIsEnabledTrue(String env, String key);
}
