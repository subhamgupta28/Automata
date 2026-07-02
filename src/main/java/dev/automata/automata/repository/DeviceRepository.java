package dev.automata.automata.repository;

import dev.automata.automata.model.Device;
import dev.automata.automata.model.Status;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DeviceRepository extends MongoRepository<Device, String> {

    @Override
    Optional<Device> findById(String s);

    List<Device> findByMacAddr(String macAddr);

    List<Device> findByIdIn(Collection<String> ids);

    Device findByName(String name);

    List<Device> findAllByType(String type);

    Device findByCategory(String category);

    List<Device> findAllByMacAddr(String macAddr);

    List<Device> findAllByHomeId(String homeId);

    Optional<Device> findByIdAndHomeId(String id, String homeId);

    List<Device> findAllByHomeIdIsNull();

    List<Device> findAllByHomeIdIsNullOrStatus(Status status);

    List<Device> findAllByIdInAndHomeId(List<String> deviceIds, String homeId);
}