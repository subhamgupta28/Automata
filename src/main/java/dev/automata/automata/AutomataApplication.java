package dev.automata.automata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EnableAsync
@EnableCaching
@IntegrationComponentScan
public class AutomataApplication {
    public static void main(String[] args) {
        SpringApplication.run(AutomataApplication.class, args);

    }

}
