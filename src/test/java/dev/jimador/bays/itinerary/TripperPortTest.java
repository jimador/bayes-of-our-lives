package dev.jimador.bays.itinerary;

import dev.jimador.bays.provider.GuidePlaces;
import dev.jimador.bays.provider.OfficialPageResearch;
import dev.jimador.bays.provider.TripperCityPort;

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
        var prompt = new StringBuilder();
        TripperCityPort.Generator fake = new TripperCityPort.Generator() {
            @Override public <T> T create(com.embabel.agent.api.common.OperationContext c, String m,
                    String p, Class<T> type) {
                prompt.append(p);
                return type.cast(new TripperCityPort.ResearchDraft("Current hours remain unverified",
                        List.of("https://invented.example/visit")));
            }
        };
        var port = new TripperCityPort(fake, "test", "test");
        assertThrows(IllegalStateException.class, () -> port.research("Museum", "sight",
                "https://real.example/visit page clues", "hours?", null));
        assertTrue(prompt.toString().contains("<untrusted-evidence>"));
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

    @Test void followupTargetsQuestionedStopsWithVenuePagesOnly() {
        var request = new ItineraryRequest("Miami", "Downtown", LocalDate.of(2026, 10, 14),
                LocalTime.of(10, 0), LocalTime.of(16, 0), false, false, false,
                true, false, 2, 0, "art", "steady", false, "", "local", true, true);
        var sight = new GuidePlaces.Option("Art Hall", "https://www.openstreetmap.org/node/1", "indoor", "art",
                "quiet", 10, 60, 25.7, -80.2, Map.of());
        var food = new GuidePlaces.Option("Cafe", "https://www.openstreetmap.org/node/2", "indoor", "food",
                "quiet", 10, 40, 25.71, -80.2, Map.of());
        var findings = new ItineraryResearch.Findings("Miami", Instant.now(), List.of(sight), List.of(),
                List.of(food), List.of(), List.of());
        var draft = new TripperCityPort.PlanDraft(List.of(
                new TripperCityPort.PlanChoice("Art Hall", "", "Cafe", false, "Art day", "")));
        var plan = new BayesAgent.ProposedTravelPlan(ItineraryPlanner.proposeFromDraft(request,
                findings, draft, (a,b) -> 10));
        var page = new OfficialPageResearch.Evidence("Art Hall", "https://arthall.example/",
                "retrieved", Instant.now(), "Art Hall", List.of());
        var researched = new BayesAgent.PointOfInterestFindings(findings, List.of(
                new BayesAgent.ResearchedPointOfInterest("Art Hall", sight.sourceUrl(), null,
                        "unknown", "unknown", "unknown", page),
                new BayesAgent.ResearchedPointOfInterest("Cafe", food.sourceUrl(), null,
                        "unknown", "unknown", "unknown", null)));
        var targets = BayesAgent.followupTargets(plan, researched);
        assertEquals(1, targets.size());
        assertEquals("Art Hall", targets.getFirst().place().name());
        assertTrue(targets.getFirst().question().contains("opening hours"));
    }

    @Test void plannerPromptKeepsExactOriginOutOfRemoteModelInput() {
        var request = new ItineraryRequest("Miami", "123 Private House", LocalDate.of(2026, 10, 14),
                LocalTime.of(10, 0), LocalTime.of(16, 0), false, false, false,
                false, false, 2, 0, "art", "steady", false, "allergy detail", "local", true, true);
        var sight = new GuidePlaces.Option("Art Hall", "https://www.openstreetmap.org/node/1", "indoor", "art",
                "quiet", 10, 60, 25.7, -80.2, Map.of());
        var findings = new ItineraryResearch.Findings("Miami", Instant.now(), List.of(sight), List.of(),
                List.of(), List.of(), List.of(), "America/New_York");
        var profile = new BayesAgent.OutingProfile(new ItineraryMemory.Accepted("ctx", "art", "steady", List.of()), request);
        var researched = new BayesAgent.PointOfInterestFindings(findings, List.of());
        StringBuilder prompt = new StringBuilder();
        TripperCityPort.Generator fake = new TripperCityPort.Generator() {
            @Override public <T> T create(com.embabel.agent.api.common.OperationContext c, String m,
                    String p, Class<T> type) {
                prompt.append(p);
                return type.cast(new TripperCityPort.PlanDraft(List.of(
                        new TripperCityPort.PlanChoice("Art Hall", "", "", false, "Art day", ""))));
            }
        };
        new TripperCityPort(fake, "test", "test").plan(profile, researched, null);
        assertFalse(prompt.toString().contains("123 Private House"));
        assertFalse(prompt.toString().contains("allergy detail"));
        assertTrue(prompt.toString().contains("America/New_York"));
    }
}
