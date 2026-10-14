package dev.jimador.bayes.itinerary;

import com.embabel.common.ai.classification.ClassificationRequest;
import com.embabel.common.ai.classification.ClassificationSpec;
import com.embabel.common.ai.classification.ModelProvenance;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jimador.bayes.decision.LocalItineraryDecisions;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

class ItineraryDecisionsTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final ItineraryBrief profile = new ItineraryBrief("City", LocalDate.of(2030, 10, 15),
            LocalTime.of(7, 0), LocalTime.of(14, 0), 2, 0, "art", "steady", false,
            List.of("breakfast", "sight", "lunch"), List.of("seafood", "cuban"), List.of("italian"));
    private static Itinerary.Alternative alternative(String id, String status, String cuisine) {
        var stop = new Itinerary.Stop("lunch", "N7", "Venue", LocalTime.NOON, LocalTime.of(13, 0),
                5, "OSRM", "https://www.openstreetmap.org/node/7", List.of(cuisine), "liked",
                "attributed_research_claim", "attributed_operating_window_unverified", List.of("N7"), List.of("Check menu."));
        return new Itinerary.Alternative(id, "Venue", status, List.of(stop), "Fits", status.equals("unresolved")
                ? List.of("Hours need confirmation") : List.of(), List.of("Prices can change"));
    }
    @Test void localRulesSelectAUsableOutlineAndReportCoverageAndConfidenceDespiteAdvisories() {
        var alternatives = List.of(alternative("a", "usable", "seafood"), alternative("b", "usable", "cuban"));
        var answer = ItineraryDecisions.evaluate(DecisionBrief.from(profile, alternatives), alternatives,
                new LocalItineraryDecisions(mapper), mapper);
        assertEquals("true", answer.coverage());
        assertEquals("a", answer.choice());
        assertEquals("strong", answer.rating());
        assertEquals("usable-outline", answer.classification());
        assertEquals("local/bayes-itinerary-rules-v2", answer.model());
    }
    @Test void invalidAlternativesAreExcludedFromChoicesAndCannotBeReturnedByABackend() {
        var alternatives = List.of(alternative("a", "usable", "seafood"), alternative("b", "invalid", "cuban"));
        var service = spy(new LocalItineraryDecisions(mapper));
        doAnswer(invocation -> {
            ClassificationRequest request = invocation.getArgument(0);
            if (request.getSpec().getCategories().stream().anyMatch(c -> c.getId().equals("research"))) {
                assertFalse(request.getSpec().getCategories().stream().anyMatch(c -> c.getId().equals("b")));
                assertFalse(mapper.readValue(request.getInput(), DecisionBrief.class).alternatives().stream().anyMatch(a -> a.id().equals("b")));
                return ClassificationSpec.builder().category("b", "Malicious stale choice").build()
                        .selected("b", new ModelProvenance("fixture", "fixture"));
            }
            return invocation.callRealMethod();
        }).when(service).classify(any());
        var answer = ItineraryDecisions.evaluate(DecisionBrief.from(profile, alternatives), alternatives, service, mapper);
        assertTrue(answer.choice().startsWith("failure:"));
        assertNotEquals("b", answer.choice());
        assertTrue(answer.failed());
        assertEquals("usable-outline", answer.classification());
    }
    @Test void classificationStillReportsReadinessWhenResearchIsSelected() {
        var alternatives = List.of(alternative("a", "unresolved", "seafood"));
        var service = spy(new LocalItineraryDecisions(mapper));
        doAnswer(invocation -> {
            ClassificationRequest request = invocation.getArgument(0);
            if (request.getSpec().getCategories().stream().anyMatch(c -> c.getId().equals("research")))
                return request.getSpec().selected("research", new ModelProvenance("fixture", "fixture"));
            return invocation.callRealMethod();
        }).when(service).classify(any());
        var answer = ItineraryDecisions.evaluate(DecisionBrief.from(profile, alternatives), alternatives, service, mapper);
        assertEquals("research", answer.choice());
        assertEquals("material-gaps", answer.classification());
        assertEquals("possible", answer.rating());
    }
}
