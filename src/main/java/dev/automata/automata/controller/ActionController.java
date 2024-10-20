package dev.automata.automata.controller;


import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/api/v1/main")
@Controller
@RequiredArgsConstructor
public class ActionController {

    private final SimpMessagingTemplate messagingTemplate;
}
