package dev.jimador.bayes.config;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.common.ai.decision.DecisionService;
import dev.jimador.bayes.decision.LocalItineraryDecisions;
import dev.jimador.bayes.itinerary.BayesItineraryService;
import dev.jimador.bayes.itinerary.ItineraryMemories;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DecisionConfiguration {
    @Bean("localDecision")
    DecisionService localDecision() {
        return new LocalItineraryDecisions();
    }

    @Bean("localItinerary")
    BayesItineraryService localItinerary(AgentPlatform platform, ItineraryMemories memories,
            @Qualifier("localDecision") DecisionService decision) {
        return new BayesItineraryService(platform, decision, memories);
    }

    @Bean("jevItinerary")
    BayesItineraryService jevItinerary(AgentPlatform platform, ItineraryMemories memories,
            @Qualifier("jevDecision") DecisionService decision) {
        return new BayesItineraryService(platform, decision, memories);
    }

    @Bean("openaiItinerary")
    BayesItineraryService openaiItinerary(AgentPlatform platform, ItineraryMemories memories,
            @Qualifier("openaiDecision") DecisionService decision) {
        return new BayesItineraryService(platform, decision, memories);
    }
}
