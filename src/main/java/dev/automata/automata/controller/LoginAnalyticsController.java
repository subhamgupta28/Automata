package dev.automata.automata.controller;

import dev.automata.automata.model.LoginHistory;
import dev.automata.automata.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/login-analytics")
@RequiredArgsConstructor
public class LoginAnalyticsController {

    private final AuditService auditService;

    /**
     * Get login statistics for all users (Admin only)
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/stats")
    public ResponseEntity<?> getLoginStats() {
        try {
            List<Map<String, Object>> stats = auditService.getLoginStatsByUser();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                    Map.of("error", "Failed to retrieve login statistics",
                            "message", e.getMessage())
            );
        }
    }

    /**
     * Get recent logins (Admin only)
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/recent")
    public ResponseEntity<?> getRecentLogins(@RequestParam(defaultValue = "24") int hours) {
        try {
            List<LoginHistory> logins = auditService.getRecentLogins(hours);
            return ResponseEntity.ok(logins);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                    Map.of("error", "Failed to retrieve recent logins",
                            "message", e.getMessage())
            );
        }
    }

    /**
     * Get login history for specific user (Admin only)
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/user/{email}")
    public ResponseEntity<?> getUserLoginHistory(@PathVariable String email) {
        try {
            List<LoginHistory> history = auditService.getUserLogins(email);
            return ResponseEntity.ok(history);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                    Map.of("error", "Failed to retrieve user login history",
                            "message", e.getMessage())
            );
        }
    }

    /**
     * Get summary statistics (Admin only)
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/summary")
    public ResponseEntity<?> getSummaryStats() {
        try {
            List<Map<String, Object>> stats = auditService.getLoginStatsByUser();

            Map<String, Object> summary = new HashMap<>();
            summary.put("totalUsers", stats.size());
            summary.put("totalLogins", stats.stream()
                    .mapToLong(s -> ((Number) s.get("totalLogins")).longValue())
                    .sum());

            summary.put("successfulLogins", stats.stream()
                    .mapToLong(s -> ((Number) s.get("successfulLogins")).longValue())
                    .sum());

            summary.put("failedLogins", stats.stream()
                    .mapToLong(s -> ((Number) s.get("failedLogins")).longValue())
                    .sum());
            summary.put("userStats", stats);

            return ResponseEntity.ok(summary);
        } catch (Exception e) {
            return ResponseEntity.status(500).body(
                    Map.of("error", "Failed to retrieve summary statistics",
                            "message", e.getMessage())
            );
        }
    }
}
