package dev.automata.automata.configs;

import dev.automata.automata.listener.SafeJsonTransformer;
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
import org.springframework.messaging.MessagingException;
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
    @Value("${application.mqtt.user}")
    private String user;
    @Value("${application.mqtt.password}")
    private String password;
    @Value("${application.env}")
    private String env;

    private final String clientId = "springboot-client-" + env + "-" + UUID.randomUUID();
    private final String topicDefault = "status";
    private final String topicAction = "action";
    private final String topicSendData = "sendData";
    private final String topicSendLiveData = "sendLiveData";
    private final String topicAckAction = "ackAction";
    private final String topicSys = "broker/status/#";
    private final String wledDeviceTopic = "automata-wled/#";

    private final MessageChannel mqttErrorChannel;

    // ─────────────────────────────────────────────────────────────────────────
    // MQTT CLIENT FACTORY
    // ─────────────────────────────────────────────────────────────────────────

    private MqttPahoClientFactory createMqttClient(String url) {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{url});
        options.setUserName(user);
        options.setPassword(password.toCharArray());
        options.setAutomaticReconnect(true);
        options.setKeepAliveInterval(60);
        options.setCleanSession(true);
        options.setMaxInflight(100);
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        return createMqttClient(brokerUrl);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // EXECUTORS
    // ─────────────────────────────────────────────────────────────────────────

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

    @Bean
    @Primary
    public ThreadPoolTaskExecutor taskExecutor() {
        return buildExecutor("live-data-", 2, 6, 500);
    }

    @Bean
    public ThreadPoolTaskExecutor actionExecutor() {
        return buildExecutor("action-", 2, 4, 100);
    }

    @Bean
    public ThreadPoolTaskExecutor ackActionExecutor() {
        return buildExecutor("ack-action-", 2, 4, 100);
    }

    @Bean
    public ThreadPoolTaskExecutor wledExecutor() {
        return buildExecutor("wled-", 1, 3, 50);
    }

    @Bean
    public ThreadPoolTaskExecutor mqttInputExecutor() {
        return buildExecutor("mqtt-input-", 1, 3, 50);
    }

    @Bean
    public ThreadPoolTaskExecutor sysDataExecutor() {
        return buildExecutor("sys-data-", 1, 1, 20);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CHANNELS
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
    public ExecutorChannel ackAction() {
        return new ExecutorChannel(ackActionExecutor());
    }

    /**
     * Receives raw XML strings from automata-wled/# topics.
     * The SafeJsonTransformer is deliberately NOT applied here.
     */
    @Bean
    public ExecutorChannel wledChannel() {
        return new ExecutorChannel(wledExecutor());
    }

    @Bean
    public ExecutorChannel sysData() {
        return new ExecutorChannel(sysDataExecutor());
    }

    @Bean
    public org.springframework.integration.channel.DirectChannel mqttJsonPipelineChannel() {
        return new org.springframework.integration.channel.DirectChannel();
    }
    // ─────────────────────────────────────────────────────────────────────────
    // INBOUND ADAPTER
    // Note: wledDeviceTopic is NOW included so the single adapter receives
    // all topics. wledFlow is REMOVED — it was a second subscriber on the
    // same adapter bean, causing every message to be processed twice.
    // ─────────────────────────────────────────────────────────────────────────

    @Bean
    public MqttPahoMessageDrivenChannelAdapter inbound() {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(
                        "springboot-sub-" + env,
                        mqttClientFactory(),
                        topicSendLiveData,
                        topicSendData,
                        topicAction,
                        topicDefault,
                        topicAckAction,
                        wledDeviceTopic       // ← ADDED: automata-wled/#
                        // topicSys           // uncomment when needed
                );
        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(1);
        adapter.setErrorChannel(mqttErrorChannel);
        return adapter;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // OUTBOUND
    // ─────────────────────────────────────────────────────────────────────────

    @Bean
    public MessageChannel mqttOutboundChannel() {
        return new org.springframework.integration.channel.PublishSubscribeChannel();
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttOutboundChannel")
    public MessageHandler mqttOutbound() {
        MqttPahoMessageHandler handler =
                new MqttPahoMessageHandler("springboot-pub-" + env, mqttClientFactory());
        handler.setAsync(true);
        handler.setDefaultTopic(wledDeviceTopic);
        return handler;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ERROR HANDLER
    // ─────────────────────────────────────────────────────────────────────────

    @ServiceActivator(inputChannel = "mqttErrorChannel")
    public void mqttErrorHandler(Message<?> message) {
        Throwable cause = null;
        if (message.getPayload() instanceof MessagingException me) {
            cause = me.getCause() != null ? me.getCause() : me;
        } else if (message.getPayload() instanceof Throwable t) {
            cause = t;
        }
        log.error("MQTT pipeline error: {}",
                cause != null ? cause.getMessage() : message.getPayload());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // INTEGRATION FLOWS
    //
    // The key fix is the ordering:
    //   1. Route FIRST on mqtt_receivedTopic header (header is always present)
    //   2. WLED messages go to wledChannel immediately — raw XML preserved
    //   3. All other messages go to an intermediate "mqttJsonPipeline" channel
    //   4. mqttJsonPipeline applies SafeJsonTransformer, then routes to the
    //      correct per-topic channel
    //
    // This replaces the old single flow that transformed before routing, which
    // corrupted XML payloads. wledFlow is gone entirely.
    // ─────────────────────────────────────────────────────────────────────────

    @Bean
    public IntegrationFlow mqttInFlow() {
        return IntegrationFlow.from(inbound())
                // Step 1: branch WLED off immediately — before any transformation
                .route(Message.class,
                        m -> {
                            String topic = (String) m.getHeaders().get("mqtt_receivedTopic");
                            return (topic != null && topic.startsWith("automata-wled/"))
                                    ? "wledChannel"
                                    : "mqttJsonPipelineChannel";
                        }
                        // JSON  → transform pipeline
                )
                .get();
    }

    /**
     * All non-WLED messages land here, get JSON-transformed, then are routed
     * to their per-topic handler channels.
     */
    @Bean
    public IntegrationFlow mqttJsonPipeline() {
        return IntegrationFlow.from(mqttJsonPipelineChannel())
                .transform(safeJsonTransformer)
                .route(Message.class,
                        m -> {
                            String topic = (String) m.getHeaders().get("mqtt_receivedTopic");
                            if (topic == null) return "mqttInputChannel";
                            if (topic.startsWith("broker/status/")) return "sysData";
                            return topic; // topic name == channel name for all remaining cases
                        },
                        mapping -> mapping
                                .channelMapping(topicSendLiveData, "sendLiveData")
                                .channelMapping(topicSendData, "sendData")
                                .channelMapping(topicDefault, "mqttInputChannel")
                                .channelMapping(topicAction, "action")
                                .channelMapping(topicAckAction, "ackAction")
                                .channelMapping("sysData", "sysData")
                )
                .get();
    }

    // wledFlow is DELETED.
    // It was a second subscriber on inbound() that fired for every topic,
    // not just automata-wled/#, causing all messages to be double-processed.
}