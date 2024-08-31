package dev.automata.automata.repository;

import dev.automata.automata.model.Data;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface DataRepository extends MongoRepository<Data, String> {

    List<Data> findAllByDeviceId(String deviceId);
}