package dev.automata.automata.repository;

import dev.automata.automata.model.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Date;
import java.util.List;

public interface NotificationRepository extends MongoRepository<Notification, String> {
    List<Notification> findAllBySeverityIsOrderByTimestampDesc(String severity, Pageable pageable);
}
