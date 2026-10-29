package dev.automata.automata.configs;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * MongoDB connection-pool settings. Spring Boot applies this on top of the {@code spring.mongodb.*} properties
 * (host, credentials, …) that are already configured, so nothing else changes.
 *
 * <p>Why these matter here: the app runs on virtual threads, so request concurrency is effectively unbounded and the
 * pool is the real limit on database concurrency. The driver defaults are a poor fit:
 * <ul>
 *   <li><b>max-wait-ms</b> — default is <b>120 000 ms</b>. Under overload every request would queue for up to two
 *       minutes for a connection. 5 s makes overload fail fast and visibly instead of piling up silently.</li>
 *   <li><b>min-size</b> — default 0. Warm connections avoid a latency spike every time a burst arrives after a quiet
 *       period.</li>
 *   <li><b>max-connecting</b> — default 2 caps how many connections may be <em>opened</em> at once, which slows
 *       ramp-up during a burst.</li>
 *   <li><b>max-size</b> — kept at the driver default (100). Size it to your MongoDB server: it must stay below the
 *       server's {@code net.maxIncomingConnections}, and each connection costs the server memory (matters on a Pi).</li>
 * </ul>
 *
 * <p>Properties: {@code automata.mongo.pool.max-size=100}, {@code .min-size=10}, {@code .max-wait-ms=5000},
 * {@code .max-connecting=4}. Sizing guide: {@code docs/performance-tuning.md}.
 */
@Configuration
public class MongoPoolConfig {

    @Bean
    public MongoClientSettingsBuilderCustomizer mongoPoolCustomizer(
            @Value("${automata.mongo.pool.max-size:100}") int maxSize,
            @Value("${automata.mongo.pool.min-size:10}") int minSize,
            @Value("${automata.mongo.pool.max-wait-ms:5000}") long maxWaitMs,
            @Value("${automata.mongo.pool.max-connecting:4}") int maxConnecting) {

        final int max = Math.max(1, maxSize);
        final int min = Math.max(0, Math.min(minSize, max));   // never ask for more warm connections than the maximum

        return builder -> builder.applyToConnectionPoolSettings(pool -> pool
                .maxSize(max)
                .minSize(min)
                .maxWaitTime(maxWaitMs, TimeUnit.MILLISECONDS)
                .maxConnecting(Math.max(1, maxConnecting)));
    }
}
