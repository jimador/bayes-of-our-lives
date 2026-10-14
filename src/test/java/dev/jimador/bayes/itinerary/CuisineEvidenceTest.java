package dev.jimador.bayes.itinerary;

import dev.jimador.bayes.provider.GuidePlaces;
import dev.jimador.bayes.provider.TripperCityPort;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CuisineEvidenceTest {
    private static final LocalDate DAY = LocalDate.of(2026, 10, 20);
    private static ItineraryRequest request(String likes, String dislikes) {
        return new ItineraryRequest("Fort Lauderdale", "Downtown", DAY, LocalTime.of(7, 0),
                LocalTime.of(14, 0), true, false, false, true, false, 2, 0, "art", "steady",
                false, "private allergy marker", "local", true, true, null, false, false, likes, dislikes);
    }
    private static GuidePlaces.Option place(String name, int id, int drive, Map<String, String> tags) {
        return new GuidePlaces.Option(name, "https://www.openstreetmap.org/node/" + id,
                "unknown", "unknown", "unknown", drive, 45, 26.12, -80.14, tags);
    }
    @Test void normalizesSimpleSynonymsButDoesNotEquateLatinWithCuban() {
        var request = request("Sea Food; CUBANO", "ITALIANO");
        assertEquals(List.of("seafood", "cuban"), request.likedCuisines());
        assertEquals(List.of("italian"), request.dislikedCuisines());
        assertEquals("neutral", CuisineEvidence.preference(CuisineEvidence.labels("latin_american"), request));
        assertEquals("liked", CuisineEvidence.preference(CuisineEvidence.nameClues("Southport Raw Bar"), request));
        assertEquals("disliked", CuisineEvidence.preference(CuisineEvidence.nameClues("Cafe Vico - Italian Restaurant"), request));
        assertTrue(CuisineEvidence.nameClues("Latin American Cafe").isEmpty());
    }
    @Test void ranksSupportedLikesBeforeUnknownAndExcludesDislikesUnlessOnlyOption() {
        var liked = place("Southport Raw Bar", 1, 12, Map.of("opening_hours", "Tu 11:00-20:00"));
        var unknown = place("Restaurante Brasil", 2, 4, Map.of());
        var disliked = place("Cafe Vico - Italian Restaurant", 3, 2, Map.of());
        var ranked = CuisineEvidence.ranked(List.of(unknown, disliked, liked), request("seafood", "italian"), true);
        assertEquals(List.of(liked, unknown), ranked);
        assertEquals(List.of(disliked), CuisineEvidence.ranked(List.of(disliked), request("seafood", "italian"), true));
        var closed = place("Seafood place", 4, 4, Map.of("opening_hours", "Tu off"));
        assertEquals(unknown, CuisineEvidence.ranked(List.of(closed, unknown), request("seafood", ""), true).getFirst());
        var clue = FoodEvidence.assess("lunch", liked, null, request("seafood", ""), OpeningHours.Fit.UNKNOWN);
        assertEquals("liked", clue.preference());
        assertTrue(clue.cuisine().contains("name=seafood (inferred, unverified)"));
    }
    @Test void plannerCarriesLikedCandidatePastUnknownLlmChoiceWhileRespectingTaggedClosure() {
        var sight = place("Art Hall", 10, 2, Map.of("opening_hours", "24/7"));
        var cafe = place("Breakfast Cafe", 11, 2, Map.of("cuisine", "breakfast"));
        var liked = place("Southport Raw Bar", 12, 8, Map.of("opening_hours", "Tu 11:00-20:00"));
        var unknown = place("Restaurante Brasil", 13, 3, Map.of());
        var findings = new ItineraryResearch.Findings("Fort Lauderdale", Instant.now(), List.of(sight),
                List.of(cafe), List.of(unknown, liked), List.of(), List.of(), "America/New_York");
        var draft = new TripperCityPort.PlanDraft(List.of(new TripperCityPort.PlanChoice(
                "Art Hall", "Breakfast Cafe", "Restaurante Brasil", false, "Art and lunch", "A mapped day")));
        var plan = ItineraryPlanner.proposeFromDraft(request("seafood", ""), findings, draft, (a, b) -> 5);
        assertEquals("Southport Raw Bar", plan.getFirst().stops().getLast().name());
        var disliked = place("Cafe Vico - Italian Restaurant", 14, 2, Map.of());
        var withDisliked = new ItineraryResearch.Findings("Fort Lauderdale", Instant.now(), List.of(sight),
                List.of(cafe), List.of(disliked, unknown, liked), List.of(), List.of(), "America/New_York");
        var italianDraft = new TripperCityPort.PlanDraft(List.of(new TripperCityPort.PlanChoice(
                "Art Hall", "Breakfast Cafe", disliked.name(), false, "Art and lunch", "A mapped day")));
        assertEquals("Southport Raw Bar", ItineraryPlanner.proposeFromDraft(request("seafood", "italian"),
                withDisliked, italianDraft, (a, b) -> 5).getFirst().stops().getLast().name());
        var onlyDisliked = new ItineraryResearch.Findings("Fort Lauderdale", Instant.now(), List.of(sight),
                List.of(cafe), List.of(disliked), List.of(), List.of(), "America/New_York");
        assertEquals(disliked.name(), ItineraryPlanner.proposeFromDraft(request("seafood", "italian"),
                onlyDisliked, italianDraft, (a, b) -> 5).getFirst().stops().getLast().name());
        var closed = place("Southport Raw Bar", 12, 8, Map.of("opening_hours", "Tu off"));
        var closedFindings = new ItineraryResearch.Findings("Fort Lauderdale", Instant.now(), List.of(sight),
                List.of(cafe), List.of(unknown, closed), List.of(), List.of(), "America/New_York");
        assertEquals("Restaurante Brasil", ItineraryPlanner.proposeFromDraft(request("seafood", ""),
                closedFindings, draft, (a, b) -> 5).getFirst().stops().getLast().name());
    }
}
