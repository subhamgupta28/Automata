package dev.automata.automata.repository;

import dev.automata.automata.model.Data;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface DataRepository extends MongoRepository<Data, String> {
}