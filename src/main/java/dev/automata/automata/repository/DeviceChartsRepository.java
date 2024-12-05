package dev.automata.automata.repository;

import dev.automata.automata.model.DeviceCharts;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeviceChartsRepository extends MongoRepository<DeviceCharts, String> {
    DeviceCharts findByDeviceIdAndAttributeKey(String deviceId, String attributeKey);

    List<DeviceCharts> findByShowChartTrue();

    List<DeviceCharts> findByDeviceId(String deviceId);
}
