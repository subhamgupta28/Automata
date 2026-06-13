package dev.automata.automata.repository;

import dev.automata.automata.model.RecordingSession;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface RecordingSessionRepository extends MongoRepository<RecordingSession, String> {

    List<RecordingSession> findByStatus(RecordingSession.SessionStatus status);

    List<RecordingSession> findByOrderByCreatedAtDesc();

    @Query("{ 'status': 'ACTIVE', 'deviceIds': ?0 }")
    List<RecordingSession> findActiveSessionsForDevice(String deviceId);

    /**
     * Used by condition evaluator to find PENDING sessions watching a specific device field
     */
    @Query("{ 'status': { $in: ['PENDING', 'ACTIVE'] }, 'startCondition.deviceId': ?0 }")
    List<RecordingSession> findPendingOrActiveByConditionDevice(String deviceId);

    Optional<RecordingSession> findBySourceAutomationIdAndStatus(
            String automationId, RecordingSession.SessionStatus status);
}