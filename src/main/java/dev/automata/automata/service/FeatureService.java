package dev.automata.automata.service;

import dev.automata.automata.model.FeatureToggle;
import dev.automata.automata.repository.FeatureToggleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeatureService {

    private final FeatureToggleRepository repository;

    @Value("${application.env}")
    private String env;

    // ── Read ──────────────────────────────────────────────────────────────────

    /**
     * Returns every feature stored for the current environment.
     */
    public List<FeatureToggle> getAllFeatures() {
        return repository.findAllByEnv(env);
    }

    /**
     * Returns whether a feature is enabled.
     * Result is cached in Redis under the key "features::<featureKey>".
     */
    @Cacheable(value = "features", key = "#key")
    public boolean isFeatureEnabled(String key) {
        var feature = repository.findByEnvAndFeatureKeyAndIsEnabledTrue(env, key);
        log.warn("Feature Toggle for env {} and id {} is {}", env, key, feature);
        if (feature == null) return false;
        return feature.isEnabled();
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    /**
     * Persists a new feature for the current environment.
     */
    public FeatureToggle createFeature(FeatureToggle featureToggle) {
        featureToggle.setEnv(env);
        var saved = repository.save(featureToggle);
        log.info("Created feature toggle: {}", saved.getFeatureKey());
        return saved;
    }

    /**
     * Flips the enabled flag of a feature and evicts its cached value so the
     * next call to {@link #isFeatureEnabled} reads fresh data from the DB.
     */
    @CacheEvict(value = "features", key = "#result.featureKey")
    public FeatureToggle toggleFeature(String id) {
        var feature = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Feature not found: " + id));

        feature.setEnabled(!feature.isEnabled());
        var updated = repository.save(feature);
        log.info("Toggled feature {} → enabled={}", updated.getFeatureKey(), updated.isEnabled());
        return updated;
    }

    /**
     * Deletes a feature and removes it from the Redis cache so stale data is
     * never served after deletion.
     */
    public void deleteFeature(String id) {
        var feature = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Feature not found: " + id));
        repository.deleteById(id);
        evictFeatureCache(feature.getFeatureKey());
        log.info("Deleted feature: {}", feature.getFeatureKey());
    }

    /**
     * Standalone eviction helper — called programmatically after delete.
     */
    @CacheEvict(value = "features", key = "#featureKey")
    public void evictFeatureCache(String featureKey) {
        log.info("Evicted cache for feature key: {}", featureKey);
    }
}