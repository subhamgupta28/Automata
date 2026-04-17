package dev.automata.automata.modules;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

@Component
public class RedisCleanup implements ApplicationListener<ApplicationReadyEvent> {

    private final RedisConnectionFactory connectionFactory;

    public RedisCleanup(RedisConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        connectionFactory.getConnection().serverCommands().flushDb();
        System.out.println("Redis cache cleared on startup");
    }
}
