package dev.jimador.bays;

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
        assertEquals(OpeningHours.Fit.UNKNOWN, OpeningHours.fit(
                "Mo-Su 07:00-18:00; Tu off", tue,
                LocalTime.of(8, 0), LocalTime.of(9, 0)));
        assertEquals(OpeningHours.Fit.UNKNOWN, OpeningHours.fit("PH off; Mo-Su 07:00-18:00", tue,
                LocalTime.of(7, 20), LocalTime.of(8, 0)));
    }
    @Test void refusesClosedSightAndKeepsMissingMealUnresolved() {
        var closed = place("Museum", "art", 5, Map.of("opening_hours", "Tu off"));
        var open = place("Art Hall", "art", 7, Map.of("opening_hours", "Mo-Su 07:00-18:00"));
        var plans = ItineraryPlanner.propose(request(false, true, false, ""),
                new ItineraryMemory.Accepted("ctx", "art", "steady", List.of()),
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
                new ItineraryMemory.Accepted("ctx", "art", "steady", List.of()),
                findings(List.of(sight), List.of(cafe), List.of()), (a, b) -> 5);
        assertEquals("invalid", plans.getFirst().status());
        assertTrue(plans.getFirst().openQuestions().stream().anyMatch(q -> q.contains("dietary and allergy")));
        var input = BayesAgent.itineraryDecisionInput(
                new BayesAgent.OutingProfile(new ItineraryMemory.Accepted("ctx", "art", "steady", List.of()),
                        request(true, false, true, "peanut allergy")), plans);
        assertFalse(input.contains("peanut"));
        assertTrue(input.contains("Dietary verification required: true"));
    }
    @Test void generousTaggedOutlineCanBeProvisionalWithoutTrafficBlockingEveryDay() {
        var sight = place("Art Hall", "art", 4, Map.of("opening_hours", "24/7", "fee", "no"));
        var r = new ItineraryRequest("Miami", "Downtown", LocalDate.now().plusDays(1),
                LocalTime.of(10, 0), LocalTime.of(14, 0), false, false, false,
                false, false, 2, 0, "art", "steady", false, "", "local", true, false);
        var plan = ItineraryPlanner.propose(r, new ItineraryMemory.Accepted("ctx", "art", "steady", List.of()),
                findings(List.of(sight), List.of(), List.of()), (a, b) -> 5).getFirst();
        assertEquals("provisional", plan.status());
        assertTrue(plan.openQuestions().isEmpty());
        assertEquals("tagged_open_unverified", plan.stops().getFirst().hoursFit());
    }
    @Test void accessibilityAndChildTagsNeverProveWholeOutingSuitable() {
        var sight = place("Art Hall", "art", 4, Map.of("opening_hours", "24/7", "wheelchair", "yes",
                "kids_area", "yes", "fee", "yes"));
        var r = new ItineraryRequest("Miami", "Downtown", LocalDate.now().plusDays(1),
                LocalTime.of(10, 0), LocalTime.of(14, 0), false, false, false,
                false, false, 3, 1, "art", "steady", true, "", "local", true, false);
        var plan = ItineraryPlanner.propose(r, new ItineraryMemory.Accepted("ctx", "art", "steady", List.of()),
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
