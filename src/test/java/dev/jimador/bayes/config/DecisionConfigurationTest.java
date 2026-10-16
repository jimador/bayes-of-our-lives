package dev.jimador.bayes.config;

import com.embabel.common.ai.classification.ClassificationService;
import com.embabel.common.ai.decision.DecisionService;
import com.embabel.common.ai.model.DecisionServiceRegistry;
import dev.jimador.bayes.BayesApplication;
import dev.jimador.bayes.itinerary.ItineraryRequest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = BayesApplication.class, properties = {
        "embabel.agent.platform.models.openai.api-key=fixture-openai-key",
        "embabel.agent.platform.models.typesafe.api-key=fixture-typesafe-key",
        "bays.preference-store=${java.io.tmpdir}/bayes-configuration-test/preferences.json"
})
class DecisionConfigurationTest {
    @Autowired @Qualifier("localDecision") DecisionService localDecision;
    @Autowired @Qualifier("jevDecision") DecisionService jevDecision;
    @Autowired @Qualifier("openaiDecision") DecisionService openaiDecision;
    @Autowired DecisionServiceRegistry registry;

    @Test void eachEngineRoleSelectsItsConfiguredServiceInBothFamilies() {
        var expected = Map.of("local", localDecision, "jev", jevDecision, "openai", openaiDecision);
        assertEquals(expected.keySet(), java.util.Set.copyOf(ItineraryRequest.ENGINES));
        expected.forEach((engine, bean) -> {
            assertSameService(bean, registry.decisions().byRole(engine));
            assertSameService(bean, registry.classifications().byRole(engine));
        });
    }

    // The registry may hand back an observed wrapper, so compare what the service reports about itself.
    private static void assertSameService(DecisionService expected, ClassificationService selected) {
        assertEquals(expected.getName(), selected.getName());
        assertEquals(expected.getProvider(), selected.getProvider());
    }
}
