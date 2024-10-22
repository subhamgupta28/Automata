package dev.automata.automata.repository;

import dev.automata.automata.model.Actions;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ActionRepository extends MongoRepository<Actions, String> {
    List<Actions> findByProducerDeviceIdOrConsumerDeviceId(String producerDeviceId, String consumerDeviceId);

    Actions findByProducerDeviceId(String producerDeviceId);

    Actions findByProducerDeviceIdAndProducerKey(String producerDeviceId, String producerKey);
}
