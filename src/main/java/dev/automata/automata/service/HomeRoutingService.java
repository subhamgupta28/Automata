package dev.automata.automata.service;

import dev.automata.automata.cache.DeviceHomeCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class HomeRoutingService {

    private final DeviceHomeCache deviceHomeCache;
    private final SimpMessagingTemplate ws;

    public void routeToHome(String deviceId, String subTopic, Map<String, Object> payload) {
        String homeId = deviceHomeCache.getHomeId(deviceId);
        if (homeId == null) {
            log.warn("No homeId for device '{}', dropping {} message", deviceId, subTopic);
            return;
        }

        // Reuse payload map directly as envelope — add homeId/deviceId into it
        // instead of wrapping in a new Map.of() every call
        payload.put("deviceId", deviceId);
        payload.put("homeId", homeId);

        ws.convertAndSend("/topic/home/" + homeId + "/" + subTopic, Optional.of(payload));
    }

    public void routeEvent(String deviceId, String eventType, Object eventPayload) {
        String homeId = deviceHomeCache.getHomeId(deviceId);
        if (homeId == null) return;
        ws.convertAndSend(
                "/topic/home/" + homeId + "/events",
                Optional.of(Map.of("type", eventType, "deviceId", deviceId, "payload", eventPayload))
        );
    }
}