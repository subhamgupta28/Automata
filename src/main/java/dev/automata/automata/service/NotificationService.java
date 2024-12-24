package dev.automata.automata.service;

import dev.automata.automata.model.Notification;
import dev.automata.automata.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationRepository notificationRepository;

    public void sendNotification(String message, String severity) {
        Notification notification = Notification.builder()
                .message(message)
                .severity(severity)
                .timestamp(new Date())
                .build();
        var notify = notificationRepository.save(notification);
        messagingTemplate.convertAndSend("/topic/notification", notify);
    }
}
