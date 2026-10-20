package dev.jimador.bayes.config;

import com.embabel.common.ai.decision.DecisionService;
import dev.jimador.bayes.decision.LocalItineraryDecisions;
import dev.jimador.bayes.itinerary.DecisionBriefs;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the local rules next to the Jev and OpenAI models that come from configuration. The
 * platform's decision registry picks up this bean by name, and the {@code local} role points at it.
 */
@Configuration
public class DecisionConfiguration {

    @Bean("localDecision")
    DecisionService localDecision(DecisionBriefs briefs) {
        return new LocalItineraryDecisions(briefs);
    }
}
