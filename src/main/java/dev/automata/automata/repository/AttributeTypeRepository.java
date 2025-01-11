package dev.automata.automata.repository;

import dev.automata.automata.model.AttributeType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AttributeTypeRepository extends MongoRepository<AttributeType, String> {

}
