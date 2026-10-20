package dev.jimador.bayes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Loopback city itinerary app. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class BayesApplication {
    public static void main(String[] args) {
        SpringApplication.run(BayesApplication.class, args);
    }
}
