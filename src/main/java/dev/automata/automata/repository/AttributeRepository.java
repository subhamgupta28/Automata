package dev.automata.automata.repository;

import dev.automata.automata.model.Attribute;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AttributeRepository extends MongoRepository<Attribute, String> {

    List<Attribute> findAllByDeviceId(String deviceId);
}