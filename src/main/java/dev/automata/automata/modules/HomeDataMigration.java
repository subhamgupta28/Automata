package dev.automata.automata.modules;

import dev.automata.automata.model.Home;
import dev.automata.automata.model.HomeAccess;
import dev.automata.automata.model.HomeRole;
import dev.automata.automata.repository.HomeAccessRepository;
import dev.automata.automata.repository.HomeRepository;
import dev.automata.automata.repository.UsersRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class HomeDataMigration implements ApplicationRunner {

    private final MongoTemplate mongo;
    private final HomeRepository homeRepo;
    private final HomeAccessRepository accessRepo;
    private final UsersRepository userRepo;

    // Flag so this only runs once
    @Value("${automata.migration.run-home-migration:false}")
    private boolean runMigration;

    @Override
    public void run(ApplicationArguments args) {
        if (!runMigration) return;
        log.warn("=== Running home data migration ===");

        userRepo.findAll().forEach(user -> {
            // Skip if user already has a home
            List<HomeAccess> existing = accessRepo.findAllByUserId(user.getId());
            if (!existing.isEmpty()) {
                log.info("User {} already has homes, skipping", user.getEmail());
                return;
            }

            // Create default home
            Home home = Home.builder()
                    .name(user.getFirstName() + "'s Home")
                    .ownerId(user.getId())
                    .timezone(user.getTimezone())
                    .createdAt(Instant.now())
                    .build();
            home = homeRepo.save(home);

            // Create OWNER access
            HomeAccess access = HomeAccess.builder()
                    .userId(user.getId())
                    .homeId(home.getId())
                    .role(HomeRole.OWNER)
                    .grantedAt(Instant.now())
                    .grantedByUserId(user.getId())
                    .build();
            accessRepo.save(access);

            // Migrate devices — adjust query to match your current Device schema
            String homeId = home.getId();
            Query q = Query.query(Criteria.where("userId").is(user.getId()));
            Update u = new Update().set("homeId", homeId).unset("userId");
            mongo.updateMulti(q, u, "devices");

            log.info("Migrated {} → home {}", user.getEmail(), home.getId());
        });

        // Catch orphaned devices
        Query orphans = Query.query(Criteria.where("homeId").exists(false));
        long count = mongo.count(orphans, "devices");
        if (count > 0) log.warn("{} devices have no homeId after migration!", count);

        log.warn("=== Migration complete ===");
    }
}
