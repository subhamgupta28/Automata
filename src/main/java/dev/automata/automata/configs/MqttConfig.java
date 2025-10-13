package dev.automata.automata.configs;

import dev.automata.automata.mqtt.SafeJsonTransformer;
import lombok.RequiredArgsConstructor;
import org.eclipse.paho.client.mqttv3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.TaskExecutor;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.ExecutorChannel;
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
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
@RequiredArgsConstructor
public class MqttConfig {

    private final SafeJsonTransformer safeJsonTransformer;
    @Value("${application.mqtt.url}")
    private String brokerUrl; // Your MQTT broker
    @Value("${application.mqtt.user}")
    private String user;
    @Value("${application.mqtt.password}")
    private String password;
    private final String clientId = "springboot-client-";
    private final String topicDefault = "automata/status";
    private final String topicSendLiveData = "automata/sendLiveData";
    private final String topicSendData = "automata/sendData";
    private final String topicAction = "automata/action";
    private final String topicSys = "$SYS/broker/clients/123";

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{brokerUrl});
        options.setUserName(user);
        options.setPassword(password.toCharArray());
        options.setAutomaticReconnect(true);
//        options.setConnectionTimeout(2);
        options.setKeepAliveInterval(0);
        options.setCleanSession(false);
        options.setMaxInflight(10000);

        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        factory.setConnectionOptions(options);
        return factory;
    }


    @Bean
    public MqttPahoMessageDrivenChannelAdapter inbound() {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(
                        clientId + System.currentTimeMillis() + "-sub",
                        mqttClientFactory(),
                        topicSendLiveData,
                        topicSendData,
                        topicAction,
                        topicDefault,
                        topicSys
                );

        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(1);
        return adapter;
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


    @Bean
    public IntegrationFlow mqttInFlow() {
        return IntegrationFlow.from(inbound())
                .transform(safeJsonTransformer)
                .route(Message.class,
                        m -> (String) m.getHeaders().get("mqtt_receivedTopic"),
                        mapping -> mapping
                                .channelMapping(topicSendLiveData, "sendLiveData")
                                .channelMapping(topicSendData, "sendData")
                                .channelMapping(topicDefault, "mqttInputChannel")
                                .channelMapping(topicAction, "action")
                                .channelMapping(topicSys, "sysData")
                )
                .get();
    }
    @Bean
    @Primary
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("async-task-");
        executor.initialize();
        return executor;
    }
//    @Bean
//    public ExecutorService mqttExecutor() {
//        // Thread pool for processing MQTT messages
//        return Executors.newFixedThreadPool(2);
//    }

    @Bean
    public ExecutorChannel mqttInputChannel(TaskExecutor mqttExecutor) {
        return new ExecutorChannel(mqttExecutor);
    }

    @Bean
    public ExecutorChannel sendLiveData(TaskExecutor mqttExecutor) {
        return new ExecutorChannel(mqttExecutor);
    }

    @Bean
    public ExecutorChannel sendData(TaskExecutor mqttExecutor) {
        return new ExecutorChannel(mqttExecutor);
    }

    @Bean
    public ExecutorChannel action(TaskExecutor mqttExecutor) {
        return new ExecutorChannel(mqttExecutor);
    }

    @Bean
    public ExecutorChannel sysData(TaskExecutor mqttExecutor) {
        return new ExecutorChannel(mqttExecutor);
    }

}
