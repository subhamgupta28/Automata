package dev.automata.automata.service;

import dev.automata.automata.model.Notification;
import dev.automata.automata.repository.AutomationRepository;
import dev.automata.automata.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationRepository notificationRepository;
    private final AutomationRepository automationRepository;
//    private final AutomationService automationService;

    public void sendAlert(String message, String severity) {
        Notification notification = Notification.builder()
                .message(message)
                .severity(severity)
                .timestamp(new Date())
                .build();
//        var notify = notificationRepository.save(notification);
        messagingTemplate.convertAndSend("/topic/alert", notification);
    }

    public void sendNotification(String message, String severity) {
        Notification notification = Notification.builder()
                .message(message)
                .severity(severity)
                .timestamp(new Date())
                .build();
//        var notify = notificationRepository.save(notification);
        messagingTemplate.convertAndSend("/topic/notification", notification);
    }

    public String notificationAction(String action, Map<String, Object> payload) {
        return switch (action) {
            case "stop_automation"->
                stopAutomation(payload);
            case "something_else"->
                "return success";
            default -> "done";

        };
    }

    public List<Notification> getLastFiveNotifications() {
        return notificationRepository.findAllBySeverityIsOrderByTimestampDesc("medium", PageRequest.of(0, 5));
    }

    public String stopAutomation(Map<String, Object> payload) {

        return "success";
    }

    public String test(String type) {
        sendAlert("Test", type);
        return "success";
    }
}
