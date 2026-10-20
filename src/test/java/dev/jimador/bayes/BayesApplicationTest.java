package dev.jimador.bayes;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.common.ai.model.DecisionServiceRegistry;
import dev.jimador.bayes.itinerary.ItineraryRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Boots the app without real keys and checks the agent is deployed and every engine role resolves.
 * The real test is the live check in scripts/run-live-check.sh.
 */
@SpringBootTest(
        properties = {
            "embabel.agent.platform.models.openai.api-key=fixture-openai-key",
            "embabel.agent.platform.models.typesafe.api-key=fixture-typesafe-key",
            "bays.preference-store=${java.io.tmpdir}/bayes-application-test/preferences.json"
        })
class BayesApplicationTest {

    @Autowired AgentPlatform platform;
    @Autowired DecisionServiceRegistry registry;

    @Test
    void bootsWithTheAgentAndEveryEngineRole() {
        assertEquals(
                1,
                platform.agents().stream().filter(a -> a.getName().contains("BayesAgent")).count());
        var expected = java.util.Map.of("local", "bayes-itinerary-rules-v2", "openai", "gpt-4.1");
        for (String engine : ItineraryRequest.ENGINES) {
            var decisions = registry.decisions().byRole(engine);
            var classifications = registry.classifications().byRole(engine);
            assertEquals(decisions.getName(), classifications.getName(), engine);
            if (expected.containsKey(engine))
                assertEquals(expected.get(engine), decisions.getName(), engine);
        }
    }
}
