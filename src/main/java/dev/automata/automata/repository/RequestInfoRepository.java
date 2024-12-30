package dev.automata.automata.repository;

import dev.automata.automata.model.RequestInfo;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RequestInfoRepository extends MongoRepository<RequestInfo, String> {
}
