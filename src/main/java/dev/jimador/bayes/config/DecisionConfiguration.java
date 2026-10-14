package dev.jimador.bayes.config;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.api.annotation.support.AgentMetadataReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.embabel.common.ai.decision.DecisionService;
import dev.jimador.bayes.itinerary.BayesAgent;
import dev.jimador.bayes.decision.LocalItineraryDecisions;
import dev.jimador.bayes.itinerary.BayesItineraryService;
import dev.jimador.bayes.itinerary.ItineraryMemories;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DecisionConfiguration {
    @Bean("localDecision")
    DecisionService localDecision(ObjectMapper mapper) {
        return new LocalItineraryDecisions(mapper);
    }

    @Bean("localItinerary")
    BayesItineraryService localItinerary(AgentPlatform platform, ItineraryMemories memories,
            BayesAgent agent, AgentMetadataReader reader,
            @Qualifier("localDecision") DecisionService decision) {
        return new BayesItineraryService(platform, decision, memories, agent, reader);
    }

    @Bean("jevItinerary")
    BayesItineraryService jevItinerary(AgentPlatform platform, ItineraryMemories memories,
            BayesAgent agent, AgentMetadataReader reader,
            @Qualifier("jevDecision") DecisionService decision) {
        return new BayesItineraryService(platform, decision, memories, agent, reader);
    }

    @Bean("openaiItinerary")
    BayesItineraryService openaiItinerary(AgentPlatform platform, ItineraryMemories memories,
            BayesAgent agent, AgentMetadataReader reader,
            @Qualifier("openaiDecision") DecisionService decision) {
        return new BayesItineraryService(platform, decision, memories, agent, reader);
    }
}
