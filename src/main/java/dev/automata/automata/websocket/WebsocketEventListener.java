package dev.automata.automata.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebsocketEventListener {


    private final SimpMessageSendingOperations messagingTemplate;


    @EventListener
    public void WebsocketDisconnectListener(
            SessionDisconnectEvent sessionDisconnectEvent
    ) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(sessionDisconnectEvent.getMessage());
        String username = (String) headerAccessor.getSessionAttributes().get("username");
        if (username != null) {
            log.info("Websocket disconnected from " + username);
//            var chatMessage = ChatMessage.builder().type(MessageType.LEAVE)
//                    .sender(username)
//                    .build();
//            messagingTemplate.convertAndSend("/topic/public",chatMessage);
        }

    }

    @EventListener
    public void WebsocketConnectListener(
            SessionConnectEvent sessionConnectEvent
    ) {
        System.out.println("Websocket connected");
//        log.error("Websocket connected");
    }
}
