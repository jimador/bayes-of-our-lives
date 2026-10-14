package dev.jimador.bayes.itinerary;

import dev.jimador.bayes.provider.GuidePlaces;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItineraryTest {
    private static ItineraryRequest request(boolean breakfast, boolean lunch, boolean access, String dietary) {
        return new ItineraryRequest("Miami, Florida", "Downtown Miami", LocalDate.of(2026, 10, 13),
                LocalTime.of(7, 0), LocalTime.of(14, 0), breakfast, false, !breakfast,
                lunch, false, 2, 0, "art", "steady", access, dietary, "local", true, false);
    }
    private static GuidePlaces.Option place(String name, String type, int drive, Map<String, String> tags) {
        return new GuidePlaces.Option(name, "https://www.openstreetmap.org/node/123", "unknown", type,
                "unknown", drive, 45, 25.774, -80.194, tags);
    }
    private static ItineraryResearch.Findings findings(List<GuidePlaces.Option> sights,
            List<GuidePlaces.Option> cafes, List<GuidePlaces.Option> restaurants) {
        return new ItineraryResearch.Findings("Miami", Instant.parse("2026-10-12T00:00:00Z"), sights,
                cafes, restaurants, List.of("public OSM"), List.of());
    }
    @Test void asksAboutEarlyFoodBeforePlanning() {
        var r = new ItineraryRequest("Miami", "Downtown", LocalDate.of(2026, 10, 13),
                LocalTime.of(7, 0), LocalTime.of(14, 0), false, false, false, true,
                false, 2, 0, "art", "steady", false, "", "local", true, false);
        assertTrue(r.questions().stream().anyMatch(q -> q.contains("breakfast or coffee")));
        assertThrows(IllegalArgumentException.class, () -> new ItineraryRequest("Miami", "Downtown",
                LocalDate.of(2026, 10, 13), LocalTime.of(10, 0), LocalTime.of(14, 0),
                false, false, true, false, false, 2, 0, "art", "steady", false, "",
                "openai", true, false).validateShape());
        assertThrows(IllegalArgumentException.class, () -> r.withEngine("clef").validateShape());
    }
    @Test void parsesOnlySupportedOpeningHours() {
        var tue = LocalDate.of(2026, 10, 13);
        assertEquals(OpeningHours.Fit.TAGGED_CLOSED, OpeningHours.fit(
                "Mo,Fr-Su 11:00-18:00; Tu-We off; Th 11:00-21:00", tue,
                LocalTime.of(11, 0), LocalTime.of(12, 0)));
        assertEquals(OpeningHours.Fit.TAGGED_OPEN, OpeningHours.fit("Mo-Su 07:00-18:00", tue,
                LocalTime.of(7, 20), LocalTime.of(8, 0)));
        assertEquals(OpeningHours.Fit.TAGGED_OPEN, OpeningHours.fit(
                "Mo-Su 07:00-12:00; Mo-Su 13:00-18:00", tue,
                LocalTime.of(8, 0), LocalTime.of(9, 0)));
        assertEquals(OpeningHours.Fit.TAGGED_OPEN, OpeningHours.fit(
                "Tu 07:00-08:00; Tu 08:00-10:00", tue,
                LocalTime.of(7, 30), LocalTime.of(8, 10)));
        assertEquals(OpeningHours.Fit.TAGGED_CLOSED, OpeningHours.fit(
                "Tu 07:00-08:00; Tu 08:30-10:00", tue,
                LocalTime.of(7, 30), LocalTime.of(8, 40)));
        assertEquals(OpeningHours.Fit.UNKNOWN, OpeningHours.fit(
                "Mo-Su 07:00-18:00; Tu off", tue,
                LocalTime.of(8, 0), LocalTime.of(9, 0)));
        assertEquals(OpeningHours.Fit.UNKNOWN, OpeningHours.fit("PH off; Mo-Su 07:00-18:00", tue,
                LocalTime.of(7, 20), LocalTime.of(8, 0)));
    }
    @Test void blankPlannerCafeCanUseMappedLeadWithoutPretendingBreakfastIsConfirmed() {
        var sight = place("Art Hall", "art", 5, Map.of("opening_hours", "24/7"));
        var cafe = place("Cafe Siena", "food", 5, Map.of());
        var draft = new dev.jimador.bayes.provider.TripperCityPort.PlanDraft(List.of(
                new dev.jimador.bayes.provider.TripperCityPort.PlanChoice(
                        "Art Hall", "", "", false, "Art morning", "Cafe service is unknown.")));
        var plans = ItineraryPlanner.proposeFromDraft(request(true, false, false, ""),
                findings(List.of(sight), List.of(cafe), List.of()), draft, (a, b) -> 5);
        var plan = plans.getFirst();
        var breakfast = plan.stops().getFirst();
        assertEquals("Cafe Siena", breakfast.name());
        assertEquals("unresolved", plan.status());
        assertEquals("full_breakfast_unknown", breakfast.mealServiceEvidence());
        assertTrue(plan.openQuestions().stream().anyMatch(q -> q.contains("full breakfast service")
                && q.contains("Cafe Siena")));
        assertFalse(breakfast.openQuestions().contains("No mapped venue was returned"));
    }
    @Test void waitsForKnownOpeningWindowBeforeSchedulingSight() {
        var sight = place("Art Hall", "art", 5, Map.of("opening_hours", "Tu 11:00-16:00"));
        var cafe = place("Breakfast Cafe", "food", 5, Map.of("opening_hours", "24/7"));
        var lunch = place("Seafood House", "food", 5, Map.of("opening_hours", "Tu 11:00-16:00", "cuisine", "seafood"));
        var draft = new dev.jimador.bayes.provider.TripperCityPort.PlanDraft(List.of(
                new dev.jimador.bayes.provider.TripperCityPort.PlanChoice(
                        "Art Hall", "Breakfast Cafe", "Seafood House", false, "Art day", "Mapped choices")));
        var plan = ItineraryPlanner.proposeFromDraft(request(true, true, false, ""),
                findings(List.of(sight), List.of(cafe), List.of(lunch)), draft, (a, b) -> 5).getFirst();
        assertNotEquals("invalid", plan.status());
        var art = plan.stops().stream().filter(s -> s.kind().equals("sight")).findFirst().orElseThrow();
        assertEquals(LocalTime.of(11, 0), art.startsAt());
        assertEquals("tagged_open_unverified", art.hoursFit());
        assertTrue(art.openQuestions().stream().anyMatch(q -> q.contains("current opening hours")));
        assertTrue(plan.stops().getLast().endsAt().isBefore(LocalTime.of(14, 0)));
    }
    @Test void repairsAllDayClosureWithDistinctMappedSight() {
        var closed = place("Closed Museum", "art", 5, Map.of("opening_hours", "Tu off"));
        var open = place("Open Gallery", "art", 6, Map.of("opening_hours", "Tu 09:00-17:00"));
        var cafe = place("Cafe", "food", 5, Map.of("opening_hours", "24/7"));
        var lunch = place("Lunch", "food", 5, Map.of("opening_hours", "Tu 11:00-16:00"));
        var draft = new dev.jimador.bayes.provider.TripperCityPort.PlanDraft(List.of(
                new dev.jimador.bayes.provider.TripperCityPort.PlanChoice(
                        "Closed Museum", "Cafe", "Lunch", false, "A", "Closed site"),
                new dev.jimador.bayes.provider.TripperCityPort.PlanChoice(
                        "Closed Museum", "Cafe", "Lunch", false, "B", "Duplicate")));
        var plans = ItineraryPlanner.proposeFromDraft(request(true, true, false, ""),
                findings(List.of(closed, open), List.of(cafe), List.of(lunch)), draft, (a, b) -> 5);
        assertEquals(2, plans.size());
        assertTrue(plans.stream().anyMatch(p -> !p.status().equals("invalid")
                && p.stops().stream().anyMatch(s -> s.name().equals("Open Gallery"))));
        assertNotEquals(plans.get(0).stops(), plans.get(1).stops());
    }
    @Test void boundedRepairDoesNotClaimImpossibleHoursFit() {
        var closed = place("Closed Museum", "art", 5, Map.of("opening_hours", "Tu off"));
        var draft = new dev.jimador.bayes.provider.TripperCityPort.PlanDraft(List.of(
                new dev.jimador.bayes.provider.TripperCityPort.PlanChoice(
                        "Closed Museum", "", "", false, "A", "No alternative")));
        var plans = ItineraryPlanner.proposeFromDraft(request(false, false, false, ""),
                findings(List.of(closed), List.of(), List.of()), draft, (a, b) -> 5);
        assertEquals(1, plans.size());
        assertEquals("invalid", plans.getFirst().status());
        assertTrue(plans.getFirst().openQuestions().stream().anyMatch(q -> q.contains("No supported sight slot")));
    }
    @Test void refusesClosedSightAndKeepsMissingMealUnresolved() {
        var closed = place("Museum", "art", 5, Map.of("opening_hours", "Tu off"));
        var open = place("Art Hall", "art", 7, Map.of("opening_hours", "Mo-Su 07:00-18:00"));
        var plans = ItineraryPlanner.propose(request(false, true, false, ""),
                new ItineraryMemories.Accepted("ctx", "art", "steady", List.of()),
                findings(List.of(closed, open), List.of(), List.of()), (a, b) -> 10);
        assertEquals(2, plans.size());
        assertTrue(plans.stream().anyMatch(p -> p.stops().stream().anyMatch(s -> s.name().equals("Museum"))
                && p.status().equals("invalid")));
        var viable = plans.stream().filter(p -> p.stops().stream().anyMatch(s -> s.name().equals("Art Hall")))
                .findFirst().orElseThrow();
        assertEquals("unresolved", viable.status());
        assertTrue(viable.openQuestions().stream().anyMatch(q -> q.contains("lunch")));
        assertNull(viable.stops().getLast().sourceUrl());
    }
    @Test void accessAndDietaryNeedsAreNotInferredFromTags() {
        var sight = place("Art Hall", "art", 4, Map.of("opening_hours", "24/7", "wheelchair", "no"));
        var cafe = place("Cafe", "bakery", 5, Map.of("opening_hours", "24/7"));
        var plans = ItineraryPlanner.propose(request(true, false, true, "peanut allergy"),
                new ItineraryMemories.Accepted("ctx", "art", "steady", List.of()),
                findings(List.of(sight), List.of(cafe), List.of()), (a, b) -> 5);
        assertEquals("invalid", plans.getFirst().status());
        assertTrue(plans.getFirst().openQuestions().stream().anyMatch(q -> q.contains("dietary and allergy")));
        var input = BayesAgent.itineraryDecisionInput(
                new BayesAgent.OutingProfile(new ItineraryMemories.Accepted("ctx", "art", "steady", List.of()),
                        request(true, false, true, "peanut allergy")), plans);
        assertFalse(input.contains("peanut"));
        assertTrue(input.contains("Dietary verification required: true"));
    }
    @Test void taggedOpenOutlineStillAsksForCurrentHoursWithoutInventingTraffic() {
        var sight = place("Art Hall", "art", 4, Map.of("opening_hours", "24/7", "fee", "no"));
        var r = new ItineraryRequest("Miami", "Downtown", LocalDate.now().plusDays(1),
                LocalTime.of(10, 0), LocalTime.of(14, 0), false, false, false,
                false, false, 2, 0, "art", "steady", false, "", "local", true, false);
        var plan = ItineraryPlanner.propose(r, new ItineraryMemories.Accepted("ctx", "art", "steady", List.of()),
                findings(List.of(sight), List.of(), List.of()), (a, b) -> 5).getFirst();
        assertEquals("unresolved", plan.status());
        assertTrue(plan.openQuestions().stream().anyMatch(q -> q.contains("current opening hours")));
        assertEquals("tagged_open_unverified", plan.stops().getFirst().hoursFit());
    }
    @Test void accessibilityAndChildTagsNeverProveWholeOutingSuitable() {
        var sight = place("Art Hall", "art", 4, Map.of("opening_hours", "24/7", "wheelchair", "yes",
                "kids_area", "yes", "fee", "yes"));
        var r = new ItineraryRequest("Miami", "Downtown", LocalDate.now().plusDays(1),
                LocalTime.of(10, 0), LocalTime.of(14, 0), false, false, false,
                false, false, 3, 1, "art", "steady", true, "", "local", true, false);
        var plan = ItineraryPlanner.propose(r, new ItineraryMemories.Accepted("ctx", "art", "steady", List.of()),
                findings(List.of(sight), List.of(), List.of()), (a, b) -> 5).getFirst();
        assertEquals("unresolved", plan.status());
        assertTrue(plan.openQuestions().stream().anyMatch(q -> q.contains("whole route")));
        assertTrue(plan.openQuestions().stream().anyMatch(q -> q.contains("children")));
        assertTrue(plan.openQuestions().stream().anyMatch(q -> q.contains("admission price")));
    }

    @Test void jacksonAcceptsRequestsWithoutOptionalMemoryFields() throws Exception {
        String json = """
            {"city":"Miami","startingLocation":"Downtown","date":"2026-10-14",
             "startTime":"10:00","endTime":"14:00","partySize":2,"childrenCount":0,
             "interests":"art","pace":"steady","engine":"local","publicMapConsent":true}
            """;
        var request = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules()
                .readValue(json, ItineraryRequest.class);
        assertEquals("Miami", request.city());
        assertFalse(request.rememberPreferences());
    }

}
