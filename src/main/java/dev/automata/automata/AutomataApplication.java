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
        // Automatic profile detection if not already specified
        if (System.getProperty("spring.profiles.active") == null && System.getenv("SPRING_PROFILES_ACTIVE") == null) {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                System.setProperty("spring.profiles.active", "dev");
            } else {
                System.setProperty("spring.profiles.active", "prod");
            }
        }
        SpringApplication.run(AutomataApplication.class, args);
    }

}
