package dev.automata.automata.repository;

import dev.automata.automata.model.EnergyStat;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Date;
import java.util.List;

public interface EnergyStatRepository extends MongoRepository<EnergyStat, String> {
    Object findByDeviceIdAndUpdateDateContains(String deviceId, Date updateDate);

    List<EnergyStat> findAllByDeviceIdAndTimestampBetween(String deviceId, Long timestampAfter, Long timestampBefore);
}
