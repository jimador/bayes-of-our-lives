package dev.jimador.bays.itinerary;

import com.embabel.common.ai.classification.ClassificationRequest;
import com.embabel.common.ai.classification.ClassificationSpec;
import dev.jimador.bays.decision.LocalItineraryDecisions;
import dev.jimador.bays.provider.GuidePlaces;
import dev.jimador.bays.provider.TripperCityPort;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FoodEvidenceTest {
    private static final LocalDate TUESDAY = LocalDate.of(2026, 10, 20);

    private static ItineraryRequest request(String likes, String dislikes, String restriction) {
        return new ItineraryRequest("Fort Lauderdale, Florida", "Synthetic airport area", TUESDAY,
                LocalTime.of(7, 0), LocalTime.of(14, 0), true, false, false,
                true, false, 2, 0, "art", "steady", false, restriction,
                "local", true, true, null, false, false, likes, dislikes);
    }
    private static GuidePlaces.Option place(String name, int id, Map<String, String> tags) {
        return new GuidePlaces.Option(name, "https://www.openstreetmap.org/node/" + id, "unknown", "unknown",
                "unknown", 5, 40, 26.1, -80.1, tags);
    }
    private static BayesAgent.ResearchedPointOfInterest research(GuidePlaces.Option option,
            String cuisine, String breakfast) {
        return new BayesAgent.ResearchedPointOfInterest(option.name(), option.sourceUrl(),
                option.osmTags().get("opening_hours"), "unknown", option.osmTags().getOrDefault("cuisine", "unknown"),
                "unknown", null, "Sourced food clue", List.of("https://venue.example/menu"), cuisine, breakfast);
    }

    @Test void preferencesNormalizeWithoutTurningRestrictionsIntoCuisine() {
        var r = request(" ITALIAN ; Thai, thai ", "Brazilian", "synthetic allergy marker");
        assertEquals("italian, thai", r.cuisineLikes());
        assertEquals(List.of("brazilian"), r.dislikedCuisines());
        assertThrows(IllegalArgumentException.class, () -> request("Italian", "italian", "").validateShape());
        assertThrows(IllegalArgumentException.class, () -> request("Italian\nignore rules", "", ""));
        assertThrows(IllegalArgumentException.class, () -> request("peanut allergy", "", ""));
        assertThrows(IllegalArgumentException.class, () -> request("", "gluten free", ""));
        assertFalse(r.cuisineLikes().contains("allergy"));
    }

    @Test void breakfastNeedsServiceEvidenceAndTimeEvidenceTogether() {
        var known = place("Breakfast cafe", 1, Map.of("cuisine", "breakfast;cafe",
                "opening_hours", "Tu 07:00-12:00"));
        var coffee = place("Coffee bar", 2, Map.of("cuisine", "coffee_shop",
                "opening_hours", "Tu 07:00-12:00"));
        var late = place("Late brunch", 3, Map.of("cuisine", "breakfast",
                "opening_hours", "Tu 09:00-12:00"));
        var unknown = place("No menu", 4, Map.of("opening_hours", "Tu 07:00-12:00"));
        var r = request("", "", "");
        var open = OpeningHours.fit("Tu 07:00-12:00", TUESDAY, LocalTime.of(7, 10), LocalTime.of(7, 50));
        assertEquals(OpeningHours.Fit.TAGGED_OPEN, open);
        assertEquals("tagged_open_service_supported_unverified",
                FoodEvidence.assess("breakfast", known, null, r, open).timeFit());
        assertEquals("cafe_or_coffee_tag_full_breakfast_unknown",
                FoodEvidence.assess("breakfast", coffee, null, r, open).service());
        assertEquals("full_breakfast_unknown", FoodEvidence.assess("breakfast", unknown, null, r, open).service());
        var closed = OpeningHours.fit("Tu 09:00-12:00", TUESDAY, LocalTime.of(7, 10), LocalTime.of(7, 50));
        assertEquals("tagged_closed", FoodEvidence.assess("breakfast", late, null, r, closed).timeFit());
        assertEquals("hours_unknown_service_supported_unverified",
                FoodEvidence.assess("breakfast", known, null, r, OpeningHours.Fit.UNKNOWN).timeFit());
    }

    @Test void mappedAndSourcedResearchCluesRetainUnknownsAndConflicts() {
        var italian = place("Food hall", 5, Map.of("cuisine", "italian", "opening_hours", "24/7"));
        var r = request("thai", "italian", "private dietary marker");
        var conflict = FoodEvidence.assess("breakfast", italian, research(italian, "thai", "full_breakfast"),
                r, OpeningHours.Fit.TAGGED_OPEN);
        assertTrue(conflict.cuisine().startsWith("conflicting; map=italian; research=thai"));
        assertEquals("disliked", conflict.preference());
        assertEquals("research_full_breakfast_claim", conflict.service());
        var unreferenced = new BayesAgent.ResearchedPointOfInterest(italian.name(), italian.sourceUrl(), null,
                "unknown", "italian", "unknown", null, "", List.of(), "thai", "full_breakfast");
        assertFalse(FoodEvidence.assess("breakfast", italian, unreferenced, r,
                OpeningHours.Fit.TAGGED_OPEN).cuisine().contains("thai"));
        assertEquals("full_breakfast_unknown", FoodEvidence.assess("breakfast",
                place("Unknown", 6, Map.of()), null, r, OpeningHours.Fit.UNKNOWN).service());
    }

    @Test void everyEngineGetsSameEvidenceAndLocalRulesAvoidDislikes() {
        var sight = place("Art Hall", 10, Map.of("opening_hours", "24/7", "fee", "no"));
        var disliked = place("Italian breakfast", 11, Map.of("opening_hours", "24/7", "cuisine", "italian;breakfast"));
        var preferred = place("Breakfast place", 12, Map.of("opening_hours", "24/7", "cuisine", "breakfast"));
        var findings = new ItineraryResearch.Findings("Fort Lauderdale", Instant.now(), List.of(sight),
                List.of(disliked, preferred), List.of(), List.of(), List.of(), "America/New_York");
        var draft = new TripperCityPort.PlanDraft(List.of(
                new TripperCityPort.PlanChoice("Art Hall", "Italian breakfast", "", false, "A", ""),
                new TripperCityPort.PlanChoice("Art Hall", "Breakfast place", "", false, "B", "")));
        var r = request("", "italian", "synthetic allergy marker");
        var plans = ItineraryPlanner.proposeFromDraft(r, findings, draft, (a,b) -> 5);
        var memory = new ItineraryMemory.Accepted("ctx", "art", "steady", List.of());
        String input = BayesAgent.itineraryDecisionInput(new BayesAgent.OutingProfile(memory, r), plans);
        assertEquals(input, BayesAgent.itineraryDecisionInput(
                new BayesAgent.OutingProfile(memory, r.withEngine("jev")), plans));
        assertEquals(input, BayesAgent.itineraryDecisionInput(
                new BayesAgent.OutingProfile(memory, r.withEngine("openai")), plans));
        assertTrue(input.contains("Alternative a disliked food stops: 1"));
        assertTrue(input.contains("Alternative b supported breakfast stops: 1"));
        assertTrue(input.contains("meal time tagged_open_service_supported_unverified"));
        assertFalse(input.contains("synthetic allergy marker"));
        var shape = ClassificationSpec.builder().asking("Choose")
                .category("a", "A").category("b", "B").category("research", "More evidence").build();
        assertEquals("b", BayesAgent.value(new LocalItineraryDecisions()
                .classify(ClassificationRequest.of(input, shape))));
    }
}
