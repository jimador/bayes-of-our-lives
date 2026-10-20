package dev.jimador.bayes.config;

import com.embabel.common.ai.classification.ClassificationService;
import com.embabel.common.ai.decision.DecisionService;
import com.embabel.common.ai.model.DecisionServiceRegistry;
import dev.jimador.bayes.BayesApplication;
import dev.jimador.bayes.itinerary.ItineraryRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@SpringBootTest(classes = BayesApplication.class, properties = {
        "embabel.agent.platform.models.openai.api-key=fixture-openai-key",
        "embabel.agent.platform.models.typesafe.api-key=fixture-typesafe-key",
        "bays.preference-store=${java.io.tmpdir}/bayes-configuration-test/preferences.json"
})
class DecisionConfigurationTest {
    @Autowired @Qualifier("localDecision") DecisionService localDecision;
    @Autowired @Qualifier("jev-latest") DecisionService jev;
    @Autowired DecisionServiceRegistry registry;

    @Test void eachEngineRoleSelectsItsConfiguredServiceInBothFamilies() {
        assertEquals(java.util.Set.of("local", "jev", "openai"), java.util.Set.copyOf(ItineraryRequest.ENGINES));
        for (var family : java.util.List.<java.util.function.Function<String, ClassificationService>>of(
                role -> registry.decisions().byRole(role), role -> registry.classifications().byRole(role))) {
            assertSameService(localDecision, family.apply("local"));
            assertSameService(jev, family.apply("jev"));
            // There is no openai bean: the role names a chat model, served as a prompted decision service.
            var openai = family.apply("openai");
            assertEquals("gpt-4.1", openai.getName());
            assertInstanceOf(DecisionService.class, openai);
        }
    }

    // The registry may hand back an observed wrapper, so compare what the service reports about itself.
    private static void assertSameService(DecisionService expected, ClassificationService selected) {
        assertEquals(expected.getName(), selected.getName());
        assertEquals(expected.getProvider(), selected.getProvider());
    }
}
