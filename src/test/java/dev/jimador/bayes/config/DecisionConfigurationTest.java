package dev.jimador.bayes.config;

import com.embabel.common.ai.decision.DecisionService;
import dev.jimador.bayes.BayesApplication;
import dev.jimador.bayes.itinerary.BayesItineraryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

@SpringBootTest(classes = BayesApplication.class, properties = {
        "embabel.agent.platform.models.openai.api-key=fixture-openai-key",
        "embabel.agent.platform.models.typesafe.api-key=fixture-typesafe-key",
        "bays.preference-store=${java.io.tmpdir}/bayes-configuration-test/preferences.json"
})
class DecisionConfigurationTest {
    @Autowired @Qualifier("localDecision") DecisionService localDecision;
    @Autowired @Qualifier("jevDecision") DecisionService jevDecision;
    @Autowired @Qualifier("openaiDecision") DecisionService openaiDecision;
    @Autowired @Qualifier("localItinerary") BayesItineraryService local;
    @Autowired @Qualifier("jevItinerary") BayesItineraryService jev;
    @Autowired @Qualifier("openaiItinerary") BayesItineraryService openai;

    @Test void independentServicesReceiveTheActualNamedFrameworkBeans() {
        assertNotSame(local, jev);
        assertNotSame(jev, openai);
        assertSame(localDecision, ReflectionTestUtils.getField(local, "decisions"));
        assertSame(jevDecision, ReflectionTestUtils.getField(jev, "decisions"));
        assertSame(openaiDecision, ReflectionTestUtils.getField(openai, "decisions"));
    }
}
