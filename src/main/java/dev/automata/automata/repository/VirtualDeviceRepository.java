package dev.automata.automata.repository;


import dev.automata.automata.model.VirtualDevice;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface VirtualDeviceRepository extends MongoRepository<VirtualDevice, String> {
    List<VirtualDevice> findAllByTag(String tag);

    List<VirtualDevice> findAllByActive(boolean active);

    Optional<VirtualDevice> findByIdAndHomeId(String vid, String homeId);

    List<VirtualDevice> findAllByHomeId(String homeId);
}
