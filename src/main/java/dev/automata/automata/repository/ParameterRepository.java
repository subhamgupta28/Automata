package dev.automata.automata.repository;


import dev.automata.automata.model.Parameter;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ParameterRepository extends MongoRepository<Parameter, String> {
    Parameter findByDeviceId(String deviceId);
}
