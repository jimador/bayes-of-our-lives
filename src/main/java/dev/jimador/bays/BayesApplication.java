package dev.jimador.bays;

import dev.jimador.bays.decision.BayesDecisionServices;
import com.embabel.common.ai.decision.DecisionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/** Loopback city itinerary app. */
@SpringBootApplication
public class BayesApplication {
    public static void main(String[] args) {
        var app = new SpringApplication(BayesApplication.class);
        if (System.getenv("OPENAI_API_KEY") == null || System.getenv("OPENAI_API_KEY").isBlank())
            app.setDefaultProperties(java.util.Map.of("embabel.agent.platform.models.openai.api-key", "unused-local-mode"));
        app.run(args);
    }

    @Bean("baysDecisionService")
    DecisionService decisionService(@Value("${demo.engine:local}") String engine, BayesDecisionServices services) {
        return services.forItineraryEngine(engine);
    }
}
