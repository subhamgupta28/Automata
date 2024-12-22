package dev.automata.automata.service;

import dev.automata.automata.dto.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final SimpMessagingTemplate messagingTemplate;

    public void sendNotification(String message, String severity) {
        messagingTemplate.convertAndSend("/topic/notification", new Notification(message, severity));
    }
}
