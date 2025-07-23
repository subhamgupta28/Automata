package dev.automata.automata.modules;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

@Controller
public class AudioWebSocketHandler {

    private final AudioReactiveWled udpService = new AudioReactiveWled(); // Or inject

    @MessageMapping("/audio")
    public void handleTextMessage(
            @Payload AudioData payload,
            SimpMessageHeaderAccessor headerAccessor
    ) {
        try {
            System.err.println(payload);
            udpService.send(payload);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
