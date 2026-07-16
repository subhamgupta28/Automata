package dev.automata.automata.listener;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.integration.mqtt.event.MqttConnectionFailedEvent;
import org.springframework.integration.mqtt.event.MqttSubscribedEvent;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class MqttEventsListener {

    @EventListener
    public void handleConnectionFailed(MqttConnectionFailedEvent event) {
        log.error("⚠️ MQTT connection failed: ", event.getCause());
    }

    @EventListener
    public void handleSubscribed(MqttSubscribedEvent event) {
        log.info("✅ Subscribed to topics: {}", event.getMessage());
    }


}

