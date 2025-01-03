package dev.automata.automata.repository;

import dev.automata.automata.model.Attribute;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AttributeRepository extends MongoRepository<Attribute, String> {

    List<Attribute> findAllByDeviceId(String deviceId);

    List<Attribute> findAllByDeviceIdIn(List<String> deviceIds);

    List<Attribute> findByDeviceId(String deviceId);

    long deleteByDeviceId(String deviceId);

    List<Attribute> findByDeviceIdAndType(String deviceId, String type);

    List<Attribute> findByDeviceIdAndTypeNotAndVisibleIsTrue(String deviceId, String type);
    List<Attribute> findByDeviceIdAndTypeNot(String deviceId, String type);

    Attribute findByKeyAndDeviceId(String key, String deviceId);

    Attribute findByDeviceIdAndKey(String deviceId, String key);
}