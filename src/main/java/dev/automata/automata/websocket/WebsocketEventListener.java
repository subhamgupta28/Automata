package dev.automata.automata.websocket;

import dev.automata.automata.model.Data;
import dev.automata.automata.model.Status;
import dev.automata.automata.service.MainService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.HashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebsocketEventListener {


    private final SimpMessageSendingOperations messagingTemplate;
    private final MainService mainService;


    @EventListener
    public void WebsocketDisconnectListener(
            SessionDisconnectEvent sessionDisconnectEvent
    ) {
        System.out.println("Websocket Disconnected");
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(sessionDisconnectEvent.getMessage());
        String deviceId = (String) headerAccessor.getSessionAttributes().get("deviceId");
        if (deviceId != null) {
            System.err.println("Websocket disconnected from " + deviceId);
            var device = mainService.setStatus(deviceId, Status.OFFLINE);
            messagingTemplate.convertAndSend("/topic/data", device);
        }

    }

    @EventListener
    public void WebsocketConnectListener(
            SessionConnectEvent sessionConnectEvent
    ) {
        System.out.println("Websocket Connected");
    }
}
