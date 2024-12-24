package dev.automata.automata.controller;

import dev.automata.automata.model.Notification;
import dev.automata.automata.service.NotificationService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@AllArgsConstructor
@RequestMapping("/api/v1/utils")
public class UtilityController {

    private final NotificationService notificationService;

    @GetMapping("/notifications")
    public ResponseEntity<List<Notification>> getLastFiveNotifications() {
        return ResponseEntity.ok(notificationService.getLastFiveNotifications());
    }
}
