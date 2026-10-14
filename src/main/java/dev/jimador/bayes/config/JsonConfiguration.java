package dev.jimador.bayes.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JsonConfiguration {
    @Bean ObjectMapper itineraryJson() { return new ObjectMapper().findAndRegisterModules(); }
}
