package dev.automata.automata.configs;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.Transformers;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

import java.util.Map;

@Configuration
public class MqttConfig {

    @Value("${application.mqtt.url}")
    private String brokerUrl; // Your MQTT broker
    @Value("${application.mqtt.user}")
    private String user;
    @Value("${application.mqtt.password}")
    private String password;
    private final String clientId = "springboot-client";
    private final String topicDefault = "automata/message";
    private final String topicSendLiveData = "automata/sendLiveData";
    private final String topicSendData = "automata/sendData";

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{brokerUrl});
        options.setUserName(user); // if needed
        options.setPassword(password.toCharArray()); // if needed
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(20);
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        factory.setConnectionOptions(options);
        return factory;
    }

    // Outbound: publish messages
    @Bean
    @ServiceActivator(inputChannel = "mqttOutboundChannel")
    public MessageHandler mqttOutbound() {
        MqttPahoMessageHandler handler =
                new MqttPahoMessageHandler(clientId + "-pub", mqttClientFactory());
        handler.setAsync(true);
        handler.setDefaultTopic(topicDefault);
        return handler;
    }

    @Bean
    public MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }

    // Inbound: subscribe and receive messages
    @Bean
    public MqttPahoMessageDrivenChannelAdapter inbound() {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(
                        clientId + "-sub",
                        mqttClientFactory(),
                        topicSendLiveData,
                        topicSendData
                );
        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(1);
//        adapter.setOutputChannel(mqttInputChannel());
        return adapter;
    }

    @Bean
    public IntegrationFlow mqttInFlow() {
        return IntegrationFlow.from(inbound())
                .transform(Transformers.fromJson(Map.class))
                .route(Message.class,
                        m -> (String) m.getHeaders().get("mqtt_receivedTopic"),
                        mapping -> mapping
                                .channelMapping(topicSendLiveData, "sendLiveData")
                                .channelMapping(topicSendData, "sendData")
                                .channelMapping(topicDefault, "mqttInputChannel")
                )
                .get();
    }
    @Bean
    public MessageChannel mqttInputChannel() {
        return new DirectChannel();
    }
    @Bean
    public MessageChannel sendLiveData() {
        return new DirectChannel();
    }

    @Bean
    public MessageChannel sendData() {
        return new DirectChannel();
    }


//    @Bean
//    @ServiceActivator(inputChannel = "mqttInputChannel")
//    public MessageHandler handler() {
//        return message -> {
//            System.out.println("Received MQTT headers: " + message.getHeaders());
//            System.out.println("Received MQTT message: " + message.getPayload());
//        };
//    }
}
