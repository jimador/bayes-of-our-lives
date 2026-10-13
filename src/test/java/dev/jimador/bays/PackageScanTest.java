package dev.jimador.bays;

import com.embabel.agent.core.AgentPlatform;
import dev.jimador.bays.web.BayesGuideController;
import dev.jimador.bays.web.BayesItineraryController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Checks that the root application still discovers the moved agent and web adapters. */
@SpringBootTest(classes = BayesApplication.class, properties = {
        "bays.web=true", "embabel.agent.platform.models.openai.api-key=unused-local-mode"
})
class PackageScanTest {
    @Autowired AgentPlatform platform;
    @Autowired BayesGuideController guideController;
    @Autowired BayesItineraryController itineraryController;

    @Test void discoversAgentAndControllersAcrossSubpackages() {
        assertTrue(platform.agents().stream().anyMatch(agent -> agent.getName().contains("BayesAgent")));
        assertNotNull(guideController);
        assertNotNull(itineraryController);
    }
}
