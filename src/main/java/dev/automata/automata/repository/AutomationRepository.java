package dev.automata.automata.repository;

import dev.automata.automata.model.Automation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface AutomationRepository extends MongoRepository<Automation, String> {

    List<Automation> findByIsEnabledTrue();

    List<Automation> findByTrigger_DeviceId(String deviceId);

    // Instead of full documents, project only what you need for the check
    @Query(value = "{ 'isEnabled': true }", fields = "{ 'trigger': 1, 'conditions': 1, 'operators': 1, 'actions': 1, 'name': 1, 'triggerDeviceType': 1 }")
    List<Automation> findEnabledForExecution();
}
