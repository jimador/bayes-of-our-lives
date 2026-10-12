package dev.jimador.bays;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TripperPortTest {
    @Test void researchCitationsMustComeFromRetrievedEvidence() {
        TripperCityPort.Generator fake = new TripperCityPort.Generator() {
            @Override public <T> T create(com.embabel.agent.api.common.OperationContext c, String m,
                    String p, Class<T> type) {
                return type.cast(new TripperCityPort.ResearchDraft("Current hours remain unverified",
                        List.of("https://invented.example/visit")));
            }
        };
        var port = new TripperCityPort(fake, "test", "test");
        assertThrows(IllegalStateException.class, () -> port.research("Museum", "sight",
                "https://real.example/visit page clues", "hours?", null));
    }

    @Test void researchRetryStopsAfterOneAndStopsWhenNoNewEvidence() {
        var calls = new AtomicInteger();
        String result = BoundedResearchLoop.run("research", 1, "research"::equals, previous -> {
            calls.incrementAndGet(); return Optional.of("research");
        });
        assertEquals("research", result);
        assertEquals(1, calls.get());
        calls.set(0);
        result = BoundedResearchLoop.run("research", 1, "research"::equals, previous -> {
            calls.incrementAndGet(); return Optional.empty();
        });
        assertEquals("research", result);
        assertEquals(1, calls.get());
    }

    @Test void llmProposalIsScheduledButCannotOverrideMappedClosure() {
        var request = new ItineraryRequest("Miami", "Downtown", LocalDate.of(2026, 10, 13),
                LocalTime.of(9, 0), LocalTime.of(14, 0), false, false, false,
                true, false, 2, 0, "art", "steady", false, "", "local", true, false);
        var sight = new GuidePlaces.Option("Museum", "https://www.openstreetmap.org/node/1", "indoor", "art",
                "quiet", 10, 60, 25.7, -80.2, Map.of("opening_hours", "Tu off"));
        var food = new GuidePlaces.Option("Cafe", "https://www.openstreetmap.org/node/2", "indoor", "food",
                "quiet", 10, 40, 25.71, -80.2, Map.of("opening_hours", "Mo-Su 07:00-21:00"));
        var findings = new ItineraryResearch.Findings("Miami", Instant.now(), List.of(sight), List.of(),
                List.of(food), List.of(), List.of());
        var draft = new TripperCityPort.PlanDraft(List.of(
                new TripperCityPort.PlanChoice("Museum", "", "Cafe", false, "Great day", "Museum first")));
        var alternatives = ItineraryPlanner.proposeFromDraft(request, findings, draft, (a,b) -> 10);
        assertEquals("invalid", alternatives.getFirst().status());
        assertEquals("sight", alternatives.getFirst().stops().getFirst().kind());
        assertThrows(IllegalArgumentException.class, () -> ItineraryPlanner.proposeFromDraft(request,
                findings, new TripperCityPort.PlanDraft(List.of(new TripperCityPort.PlanChoice(
                        "Imaginary", "", "Cafe", false, "", ""))), (a,b) -> 10));
    }
}
