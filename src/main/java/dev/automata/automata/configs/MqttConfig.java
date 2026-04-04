package dev.automata.automata.configs;

import dev.automata.automata.mqtt.SafeJsonTransformer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.ExecutorChannel;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.UUID;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class MqttConfig {

    private final SafeJsonTransformer safeJsonTransformer;

    @Value("${application.mqtt.url}")
    private String brokerUrl;
    @Value("${application.mqtt.url_public}")
    private String brokerUrlPublic;
    @Value("${application.mqtt.user}")
    private String user;
    @Value("${application.mqtt.password}")
    private String password;

    private final String clientId = "springboot-client-" + UUID.randomUUID();
    private final String topicDefault = "status";
    private final String topicAction = "action";
    private final String topicSendData = "sendData";
    private final String topicSendLiveData = "sendLiveData";
    private final String topicSys = "broker/status/#";
    private final String wledDeviceTopic = "automata-wled/#";
    private final String wledGroupTopic = "automata-wled/all";

    // ─────────────────────────────────────────────────────────────────────────
    // MQTT CLIENT FACTORIES
    // ─────────────────────────────────────────────────────────────────────────

    private MqttPahoClientFactory createMqttClient(String url) {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{url});
        options.setUserName(user);
        options.setPassword(password.toCharArray());
        options.setAutomaticReconnect(true);
        options.setKeepAliveInterval(60);
        options.setCleanSession(true);
        options.setMaxInflight(10000);
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        return createMqttClient(brokerUrl);
    }

    @Bean
    public MqttPahoClientFactory mqttClientFactoryPublic() {
        return createMqttClient(brokerUrlPublic);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ISOLATED EXECUTORS — one per channel group
    //
    // Why isolated?  All channels previously shared one 10-thread pool.
    // A burst on sendLiveData or action would exhaust the pool and cause
    // RejectedExecutionException on sysData → crash the Paho callback
    // thread → "Lost connection: MqttException".
    //
    // CallerRunsPolicy on every executor: if the pool is full, the calling
    // thread (Paho callback) runs the task itself.  This creates backpressure
    // without ever throwing RejectedExecutionException and losing the connection.
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * High-frequency sensor readings — needs the most threads.
     * sendData + sendLiveData both use this.
     */
    /**
     * Shared helper — CallerRunsPolicy is applied to every executor.
     * This is the key change: rejection becomes backpressure, not an exception.
     */
    private ThreadPoolTaskExecutor buildExecutor(
            String prefix, int core, int max, int queueCapacity) {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(core);
        ex.setMaxPoolSize(max);
        ex.setQueueCapacity(queueCapacity);
        ex.setKeepAliveSeconds(30);
        ex.setThreadNamePrefix(prefix);
        ex.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        ex.initialize();
        return ex;
    }

// Pi 5 tuned — total max threads across all pools: ~24
// Generous queues absorb bursts without needing more threads

    @Bean
    @Primary
    public ThreadPoolTaskExecutor taskExecutor() {
        // sendLiveData + sendData — highest frequency but mostly I/O wait
        return buildExecutor("live-data-", 2, 6, 500);
    }

    @Bean
    public ThreadPoolTaskExecutor actionExecutor() {
        // action commands — moderate, latency matters more than throughput
        return buildExecutor("action-", 2, 4, 100);
    }

    @Bean
    public ThreadPoolTaskExecutor wledExecutor() {
        // LED commands — bursty but short-lived
        return buildExecutor("wled-", 1, 3, 50);
    }

    @Bean
    public ThreadPoolTaskExecutor mqttInputExecutor() {
        return buildExecutor("mqtt-input-", 1, 3, 50);
    }

    @Bean
    public ThreadPoolTaskExecutor sysDataExecutor() {
        // broker/status events — very rare, 1 thread is plenty
        return buildExecutor("sys-data-", 1, 1, 20);
    }


    // ─────────────────────────────────────────────────────────────────────────
    // CHANNELS — each wired to its own dedicated executor
    // ─────────────────────────────────────────────────────────────────────────

    @Bean
    public ExecutorChannel mqttInputChannel() {
        return new ExecutorChannel(mqttInputExecutor());
    }

    @Bean
    public ExecutorChannel sendLiveData() {
        return new ExecutorChannel(taskExecutor());
    }

    @Bean
    public ExecutorChannel sendData() {
        return new ExecutorChannel(taskExecutor());
    }

    @Bean
    public ExecutorChannel action() {
        return new ExecutorChannel(actionExecutor());
    }

    @Bean
    public ExecutorChannel wledChannel() {
        return new ExecutorChannel(wledExecutor());
    }

    /**
     * sysData now has its own tiny executor.
     * Broker status noise is completely isolated from all other channels.
     */
    @Bean
    public ExecutorChannel sysData() {
        return new ExecutorChannel(sysDataExecutor());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INBOUND ADAPTERS
    // ─────────────────────────────────────────────────────────────────────────

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
        // Errors go to errorChannel — never propagate back to Paho callback thread
        adapter.setErrorChannel(mqttErrorChannel());
        return adapter;
    }

    @Bean
    public MqttPahoMessageDrivenChannelAdapter publicInbound() {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(
                        clientId + "-public-sub",
                        mqttClientFactoryPublic(),
                        wledDeviceTopic,
                        wledGroupTopic
                );
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(1);
        adapter.setOutputChannel(mqttInputChannel());
        adapter.setErrorChannel(mqttErrorChannel());
        return adapter;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OUTBOUND HANDLERS
    // ─────────────────────────────────────────────────────────────────────────

    @Bean
    public MessageChannel mqttOutboundChannel() {
        return new org.springframework.integration.channel.PublishSubscribeChannel();
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttOutboundChannel")
    public MessageHandler mqttOutbound() {
        MqttPahoMessageHandler handler =
                new MqttPahoMessageHandler(clientId + "-pub", mqttClientFactory());
        handler.setAsync(true);
        handler.setDefaultTopic(wledDeviceTopic);
        return handler;
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttOutboundChannel")
    public MessageHandler mqttOutboundPublic() {
        MqttPahoMessageHandler handler =
                new MqttPahoMessageHandler(clientId + "-pub-public", mqttClientFactoryPublic());
        handler.setAsync(true);
        handler.setDefaultTopic(topicDefault);
        return handler;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ERROR CHANNEL — prevents any handler exception from reaching Paho
    // ─────────────────────────────────────────────────────────────────────────

    @Bean
    public MessageChannel mqttErrorChannel() {
        // DirectChannel: runs on the thread that sent to it (no extra executor needed)
        return new org.springframework.integration.channel.DirectChannel();
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttErrorChannel")
    public MessageHandler mqttErrorHandler() {
        return message -> {
            Throwable cause = null;
            if (message.getPayload() instanceof org.springframework.messaging.MessagingException me) {
                cause = me.getCause() != null ? me.getCause() : me;
            } else if (message.getPayload() instanceof Throwable t) {
                cause = t;
            }
            String topic = message.getHeaders().containsKey("mqtt_receivedTopic")
                    ? message.getHeaders().get("mqtt_receivedTopic", String.class)
                    : "unknown";
            log.error("MQTT pipeline error on topic '{}' (connection preserved): {}",
                    topic, cause != null ? cause.getMessage() : message.getPayload());
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INTEGRATION FLOWS
    // ─────────────────────────────────────────────────────────────────────────

    @Bean
    public IntegrationFlow mqttInFlow() {
        return IntegrationFlow.from(inbound())
                .transform(safeJsonTransformer)
                .route(Message.class,
                        m -> {
                            String topic = (String) m.getHeaders().get("mqtt_receivedTopic");
                            if (topic == null) return "mqttInputChannel";
                            if (topic.startsWith("broker/status/")) return "sysData";
                            if (topic.startsWith("automata-wled/")) return "mqttInputChannel";
                            return topic;
                        },
                        mapping -> mapping
                                .channelMapping(topicSendLiveData, "sendLiveData")
                                .channelMapping(topicSendData, "sendData")
                                .channelMapping(topicDefault, "mqttInputChannel")
                                .channelMapping(topicAction, "action")
                                .channelMapping("sysData", "sysData")
                )
                .get();
    }

    @Bean
    public IntegrationFlow wledFlow() {
        return IntegrationFlow.from(publicInbound())
                .enrichHeaders(h -> h.headerFunction(
                        "device",
                        m -> {
                            String topic = (String) m.getHeaders().get("mqtt_receivedTopic");
                            return topic != null ? topic.substring(13) : null;
                        }
                ))
                .channel("wledChannel")
                .handle(message -> log.debug("WLED received: {}", message.getPayload()))
                .get();
    }
}