package dev.automata.automata.modules;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

public class AudioWebSocketHandler extends TextWebSocketHandler {

    private final AudioReactiveWled udpService = new AudioReactiveWled(); // Or inject

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            AudioData data = mapper.readValue(message.getPayload(), AudioData.class);
            udpService.send(data);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
