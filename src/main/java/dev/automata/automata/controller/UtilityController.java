package dev.automata.automata.controller;

import dev.automata.automata.model.Notification;
import dev.automata.automata.service.NotificationService;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
@AllArgsConstructor
@RequestMapping("/api/v1/utils")
public class UtilityController {

    private final NotificationService notificationService;

    @GetMapping("test/{type}")
    public ResponseEntity<?> testNotify(@PathVariable String type){
        return ResponseEntity.ok(notificationService.test(type));
    }

    @GetMapping("/notifications")
    public ResponseEntity<List<Notification>> getLastFiveNotifications() {
        return ResponseEntity.ok(notificationService.getLastFiveNotifications());
    }

    @PostMapping("/action/{action}")
    public ResponseEntity<String> notificationAction(@PathVariable("action") String action, @RequestBody Map<String, Object> body) {
        return ResponseEntity.ok(notificationService.notificationAction(action, body));
    }
}
