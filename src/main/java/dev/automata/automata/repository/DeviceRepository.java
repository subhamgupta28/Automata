package dev.automata.automata.repository;

import dev.automata.automata.model.Device;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceRepository extends MongoRepository<Device, String> {

    @Override
    Optional<Device> findById(String s);

    List<Device> findByMacAddr(String macAddr);
}