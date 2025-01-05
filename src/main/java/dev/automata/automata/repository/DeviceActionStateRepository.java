package dev.automata.automata.repository;

import dev.automata.automata.model.DeviceActionState;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceActionStateRepository extends MongoRepository<DeviceActionState, String> {
}
