package dev.automata.automata.repository;


import dev.automata.automata.v2.ExecutionPlan;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ExecutionPlanRepository extends MongoRepository<ExecutionPlan, String> {
}
