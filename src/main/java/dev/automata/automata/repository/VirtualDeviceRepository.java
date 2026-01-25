package dev.automata.automata.repository;


import dev.automata.automata.model.VirtualDevice;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface VirtualDeviceRepository extends MongoRepository<VirtualDevice, String> {
    List<VirtualDevice> findAllByTag(String tag);

    List<VirtualDevice> findAllByActive(boolean active);
}
