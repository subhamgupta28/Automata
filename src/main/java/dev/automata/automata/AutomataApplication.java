package dev.automata.automata;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class AutomataApplication {
    public static void main(String[] args) {
        SpringApplication.run(AutomataApplication.class, args);

    }

}
