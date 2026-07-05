package dev.automata.automata.service;

import dev.automata.automata.model.FeatureToggle;
import dev.automata.automata.repository.FeatureToggleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeatureService {

    private final FeatureToggleRepository repository;

    @Value("${application.env}")
    private String env;

    // Caches the result so subsequent checks don't hit the DB
    @Cacheable(value = "features", key = "#key")
    public boolean isFeatureEnabled(String key) {

//        var env = System.getProperty("spring.profiles.active");
        var feature = repository.findByEnvAndFeatureKeyAndIsEnabledTrue(env, key);// Default to false if the feature isn't in the DB safely
        log.warn("Feature Toggle for env {} and id {} is {}", env, key, feature);
        if (feature == null)
            return false;
        return feature.isEnabled();
    }

    //    @PostConstruct
    public void createFeature() {
        var featureToggle = FeatureToggle.builder()
                .featureKey("PERIODIC_AUTOMATION_SERVICE")
                .description("Automation service for env " + env)
                .isEnabled(true)
                .group("SYSTEM")
                .env(env).build();
        repository.save(featureToggle);
    }
}
