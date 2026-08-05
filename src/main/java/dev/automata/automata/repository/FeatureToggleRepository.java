package dev.automata.automata.repository;

import dev.automata.automata.model.FeatureToggle;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeatureToggleRepository extends MongoRepository<FeatureToggle, String> {

    /**
     * Used by {@code isFeatureEnabled} — original query unchanged.
     */
    FeatureToggle findByEnvAndFeatureKeyAndIsEnabledTrue(String env, String featureKey);

    /**
     * Used by the management UI to list all features for the current env.
     */
    List<FeatureToggle> findAllByEnv(String env);
}