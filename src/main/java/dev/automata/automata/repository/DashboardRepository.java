package dev.automata.automata.repository;

import dev.automata.automata.model.Dashboard;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DashboardRepository extends MongoRepository<Dashboard, String> {

    Optional<Dashboard> findByDeviceId(String id);
    List<Dashboard> findByDeviceIdIn(List<String> ids);
}
