package dev.automata.automata.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class HomeRoutingService {

    private final DeviceHomeCache deviceHomeCache;
    private final SimpMessagingTemplate ws;

    public void routeToHome(String deviceId, String subTopic,
                            Map<String, Object> payload) {

//        log.info("Attempting to route message for deviceId: {}", deviceId);
        String homeId = deviceHomeCache.getHomeId(deviceId);

        if (homeId == null) {
            log.warn("routeToHome: No homeId found in cache or DB for deviceId='{}', subTopic='{}'. Message will not be routed.", deviceId, subTopic);
            return;
        }

        String destination = "/topic/home/" + homeId + "/" + subTopic;
//        log.info("Routing message for deviceId='{}' to destination: {}", deviceId, destination);

        Map<String, Object> envelope = Map.of(
                "deviceId", deviceId,
                "homeId", homeId,
                "data", payload
        );

        try {
            ws.convertAndSend(destination, envelope);
//            log.info("Successfully routed message for deviceId='{}' to {}", deviceId, destination);
        } catch (Exception e) {
            log.error("Error sending message to WebSocket destination: {}", destination, e);
        }

    }

    public void routeEvent(String deviceId, String eventType, Object eventPayload) {
        String homeId = deviceHomeCache.getHomeId(deviceId);
        if (homeId != null) {
            String destination = "/topic/home/" + homeId + "/events";
//            log.info("Routing event for deviceId='{}' to destination: {}", deviceId, destination);
            ws.convertAndSend(
                    destination,
                    Map.of("type", eventType, "deviceId", deviceId, "payload", eventPayload)
            );
        } else {
            log.warn("routeEvent: No homeId found for deviceId='{}'. Event will not be routed.", deviceId);
        }
    }
}