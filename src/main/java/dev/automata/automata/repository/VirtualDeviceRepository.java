package dev.automata.automata.repository;


import dev.automata.automata.model.VirtualDevice;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface VirtualDeviceRepository extends MongoRepository<VirtualDevice, String> {
}
