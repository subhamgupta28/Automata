package dev.automata.automata.repository;

import dev.automata.automata.model.VirtualDeviceAccess;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VirtualDeviceAccessRepository extends MongoRepository<VirtualDeviceAccess, String> {
    VirtualDeviceAccess findByVidAndUserId(String vid, String userId);

    VirtualDeviceAccess findByVid(String vid);
}
