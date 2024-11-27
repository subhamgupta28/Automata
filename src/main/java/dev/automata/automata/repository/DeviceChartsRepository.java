package dev.automata.automata.repository;

import dev.automata.automata.model.DeviceCharts;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceChartsRepository extends MongoRepository<DeviceCharts, String> {
}
