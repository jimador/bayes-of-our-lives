package dev.jimador.bayes;

import com.embabel.agent.core.AgentPlatform;
import dev.jimador.bayes.web.BayesItineraryController;
import dev.jimador.bayes.itinerary.ReplayRunner;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks that the root application still discovers the moved agent and web adapters. */
@SpringBootTest(classes = BayesApplication.class, properties = {
        "bays.web=true", "embabel.agent.platform.models.openai.api-key=unused-local-mode"
})
class PackageScanTest {
    @Autowired AgentPlatform platform;
    @Autowired BayesItineraryController itineraryController;
    @Autowired ReplayRunner replayRunner;

    @Test void discoversAgentAndControllersAcrossSubpackages() {
        var bayes = platform.agents().stream().filter(agent -> agent.getName().contains("BayesAgent"))
                .findFirst().orElseThrow();
        assertEquals(1, bayes.getGoals().size());
        assertTrue(bayes.getGoals().iterator().next().getName().contains("completeItinerary"));
        assertNotNull(itineraryController);
        assertEquals(2, org.springframework.test.util.ReflectionTestUtils.getField(replayRunner, "runs"));
    }
}
