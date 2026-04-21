package dev.automata.automata.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PendingDelivery {
    private String correlationId;
    private String automationId;
    private String automationName;
    private String deviceId;
    private String deviceName;
    private Map<String, Object> payload;   // full payload to re-send on retry
    private int attempts;
    private int maxAttempts;
    private long firstSentAt;
    private long lastSentAt;
}
