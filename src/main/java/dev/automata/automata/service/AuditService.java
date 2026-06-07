package dev.automata.automata.service;

import dev.automata.automata.model.LoginHistory;
import dev.automata.automata.model.Users;
import dev.automata.automata.repository.LoginHistoryRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuditService {
    
    private final LoginHistoryRepository loginHistoryRepository;
    
    /**
     * Log successful login
     */
    public void logSuccessfulLogin(Users user, HttpServletRequest request) {
        LoginHistory history = LoginHistory.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .ipAddress(extractIpAddress(request))
                .userAgent(request.getHeader("User-Agent"))
                .browser(extractBrowser(request))
                .operatingSystem(extractOS(request))
                .success(true)
                .loginTime(Instant.now())
                .build();
        
        loginHistoryRepository.save(history);
    }
    
    /**
     * Log failed login attempt
     */
    public void logFailedLogin(String email, HttpServletRequest request, String reason) {
        LoginHistory history = LoginHistory.builder()
                .email(email)
                .ipAddress(extractIpAddress(request))
                .userAgent(request.getHeader("User-Agent"))
                .browser(extractBrowser(request))
                .operatingSystem(extractOS(request))
                .success(false)
                .failureReason(reason)
                .loginTime(Instant.now())
                .build();
        
        loginHistoryRepository.save(history);
    }
    
    /**
     * Log logout
     */
    public void logLogout(String userId) {
        // Find the most recent login for this user
        var logins = loginHistoryRepository.findByUserIdOrderByLoginTimeDesc(userId);
        if (!logins.isEmpty()) {
            LoginHistory lastLogin = logins.get(0);
            if (lastLogin.getLogoutTime() == null) {
                lastLogin.setLogoutTime(Instant.now());
                lastLogin.setSessionDurationSeconds(
                    (lastLogin.getLogoutTime().getEpochSecond() - 
                     lastLogin.getLoginTime().getEpochSecond())
                );
                loginHistoryRepository.save(lastLogin);
            }
        }
    }
    
    /**
     * Get all logins for a user
     */
    public List<LoginHistory> getUserLogins(String email) {
        return loginHistoryRepository.findByEmailOrderByLoginTimeDesc(email);
    }
    
    /**
     * Get recent logins across all users
     */
    public List<LoginHistory> getRecentLogins(int hours) {
        Instant since = Instant.now().minusSeconds(hours * 3600L);
        return loginHistoryRepository.findByLoginTimeAfterOrderByLoginTimeDesc(since);
    }
    
    /**
     * Get login statistics by user
     */
    public List<Map<String, Object>> getLoginStatsByUser() {
        List<LoginHistory> allLogins = loginHistoryRepository.findAll();
        
        return allLogins.stream()
                .collect(Collectors.groupingBy(LoginHistory::getEmail))
                .entrySet().stream()
                .map(entry -> {
                    Map<String, Object> stats = new HashMap<>();
                    String email = entry.getKey();
                    List<LoginHistory> userLogins = entry.getValue();
                    
                    stats.put("email", email);
                    stats.put("firstName", userLogins.get(0).getFirstName());
                    stats.put("lastName", userLogins.get(0).getLastName());
                    stats.put("totalLogins", userLogins.size());
                    stats.put("successfulLogins", userLogins.stream().filter(LoginHistory::isSuccess).count());
                    stats.put("failedLogins", userLogins.stream().filter(l -> !l.isSuccess()).count());
                    stats.put("lastLogin", userLogins.stream()
                            .filter(LoginHistory::isSuccess)
                            .map(LoginHistory::getLoginTime)
                            .max(Instant::compareTo)
                            .orElse(null));
                    
                    // Get unique locations
                    stats.put("uniqueIps", userLogins.stream()
                            .map(LoginHistory::getIpAddress)
                            .distinct()
                            .collect(Collectors.toList()));
                    
                    // Get devices used
                    stats.put("devices", userLogins.stream()
                            .map(l -> l.getBrowser() + " on " + l.getOperatingSystem())
                            .distinct()
                            .collect(Collectors.toList()));
                    
                    return stats;
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Extract client IP address from request
     */
    private String extractIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        // X-Forwarded-For can contain multiple IPs, get the first one
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
    
    /**
     * Extract browser name from User-Agent header
     */
    private String extractBrowser(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null) return "Unknown";
        
        if (userAgent.contains("Chrome")) return "Chrome";
        if (userAgent.contains("Safari")) return "Safari";
        if (userAgent.contains("Firefox")) return "Firefox";
        if (userAgent.contains("Edge")) return "Edge";
        if (userAgent.contains("Opera")) return "Opera";
        if (userAgent.contains("MSIE") || userAgent.contains("Trident")) return "IE";
        return "Other";
    }
    
    /**
     * Extract operating system from User-Agent header
     */
    private String extractOS(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        if (userAgent == null) return "Unknown";
        
        if (userAgent.contains("Windows")) return "Windows";
        if (userAgent.contains("Mac")) return "macOS";
        if (userAgent.contains("Linux")) return "Linux";
        if (userAgent.contains("Android")) return "Android";
        if (userAgent.contains("iPhone") || userAgent.contains("iPad")) return "iOS";
        return "Other";
    }
}
