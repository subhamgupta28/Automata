package dev.automata.automata.mqtt;

import org.springframework.context.event.EventListener;
import org.springframework.integration.mqtt.event.MqttConnectionFailedEvent;
import org.springframework.integration.mqtt.event.MqttSubscribedEvent;
import org.springframework.stereotype.Component;

@Component
public class MqttEventsListener {

    @EventListener
    public void handleConnectionFailed(MqttConnectionFailedEvent event) {
        System.out.println("⚠️ MQTT connection failed: " + event.getCause());
    }

    @EventListener
    public void handleSubscribed(MqttSubscribedEvent event) {
        System.out.println("✅ Subscribed to topics: " + event.getMessage());
    }


}

