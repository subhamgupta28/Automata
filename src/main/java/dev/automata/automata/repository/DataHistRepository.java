package dev.automata.automata.repository;

import dev.automata.automata.model.DataHist;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DataHistRepository extends MongoRepository<DataHist, String> {
}
