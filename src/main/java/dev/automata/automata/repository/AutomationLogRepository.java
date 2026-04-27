package dev.automata.automata.repository;

import dev.automata.automata.model.AutomationLog;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Date;
import java.util.List;

public interface AutomationLogRepository extends MongoRepository<AutomationLog, String> {
    List<AutomationLog> findByAutomationIdOrderByTimestampDesc(String automationId);

    List<AutomationLog> findByTimestampAfterOrderByTimestampDesc(Date after);

    List<AutomationLog> findByStatusOrderByTimestampDesc(AutomationLog.LogStatus status);

    List<AutomationLog> findByAutomationIdAndTimestampAfter(String automationId, Date cutoffDate);

    long countByAutomationIdAndStatusAndTimestampAfter(String id, AutomationLog.LogStatus logStatus, Date cutoffDate);

    List<AutomationLog> findByStatusAndTimestampAfter(AutomationLog.LogStatus logStatus, Date cutoffDate);

    List<AutomationLog> findByAutomationIdAndTimestampAfterAndStatusEquals(String automationId, Date timestampAfter, AutomationLog.LogStatus status);

    void deleteByAutomationId(String id);
}