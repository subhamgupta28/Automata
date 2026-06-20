package dev.automata.automata.repository;

import dev.automata.automata.model.HomeAccess;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface HomeAccessRepository extends MongoRepository<HomeAccess, String> {
    Optional<HomeAccess> findByHomeIdAndUserId(String homeId, String userId);

    List<HomeAccess> findAllByUserId(String userId);

    List<HomeAccess> findAllByHomeId(String homeId);

    void deleteAllByHomeId(String homeId);
}
