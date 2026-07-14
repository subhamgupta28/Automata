package dev.automata.automata.configs;

import dev.automata.automata.automation_engine.PlanInvalidationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Wires PlanInvalidationListener to the Redis pub/sub channel.
 * Channel name must match AutomationOrchestrator.PLAN_INVALIDATE_CHANNEL.
 */
@Configuration
public class RedisListenerConfig {

    private static final String PLAN_INVALIDATE_CHANNEL = "automation:plan:invalidated";

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            PlanInvalidationListener listener) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listener, new ChannelTopic(PLAN_INVALIDATE_CHANNEL));
        return container;
    }
}