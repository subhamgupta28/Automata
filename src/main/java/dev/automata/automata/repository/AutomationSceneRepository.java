package dev.automata.automata.repository;

import dev.automata.automata.model.AutomationScene;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface AutomationSceneRepository extends MongoRepository<AutomationScene, String> {
    /**
     * Find all scenes that contain a given automation member.
     */
    @Query("{ 'members.automationId': ?0 }")
    List<AutomationScene> findByMemberAutomationId(String automationId);
}
