package dev.automata.automata.modules;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class RedisCleanup implements ApplicationListener<ApplicationReadyEvent> {

//    private final RedisConnectionFactory connectionFactory;

//    public RedisCleanup(RedisConnectionFactory connectionFactory) {
//        this.connectionFactory = connectionFactory;
//    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
//        connectionFactory.getConnection().serverCommands().flushDb();
//        log.info("Redis cache cleared on startup");
    }
}
