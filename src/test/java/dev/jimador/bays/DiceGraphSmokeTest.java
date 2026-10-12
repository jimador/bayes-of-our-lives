package dev.jimador.bays;

import com.embabel.dice.proposition.PropositionRepository;
import com.embabel.dice.proposition.PropositionStoreType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in graph check; the initializer owns the disposable container. */
@EnabledIfSystemProperty(named = "bays.dice-smoke", matches = "true")
@ContextConfiguration(initializers = OwnedNeo4j.class)
@SpringBootTest(classes = BayesApplication.class, properties = {
        "demo.dice.enabled=true", "bays.mode=fixed", "bays.profile=art-quiet",
        "embabel.agent.platform.models.openai.api-key=unused-local-mode"
})
class DiceGraphSmokeTest {
    @Autowired GuideMemories memories;
    @Autowired PropositionRepository repository;

    @Test void seededFictionalFactsRoundTripThroughOwnedGraph() {
        assertEquals(PropositionStoreType.STORED, repository.getStoreType());
        var snapshot = memories.read("art-quiet");
        assertEquals("art", snapshot.sightInterest());
        assertEquals(4, snapshot.facts().size());
        assertTrue(snapshot.provenance().contains("store=stored"));
    }
}
