package dev.automata.automata.repository;


import dev.automata.automata.automation_engine.ExecutionPlan;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ExecutionPlanRepository extends MongoRepository<ExecutionPlan, String> {
}
