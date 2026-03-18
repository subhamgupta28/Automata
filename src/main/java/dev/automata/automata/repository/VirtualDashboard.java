package dev.automata.automata.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VirtualDashboard extends MongoRepository<VirtualDashboard, String> {
}
