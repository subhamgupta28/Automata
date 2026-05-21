package dev.automata.automata.controller;

import dev.automata.automata.dto.Webhook;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@RequestMapping("/webhook")
@Controller
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    @PostMapping
    public ResponseEntity<?> event(
            @RequestBody Webhook webhook
    ) {
        log.info("Webhook received [{}]", webhook);

        return ResponseEntity.ok("success");
    }
}
