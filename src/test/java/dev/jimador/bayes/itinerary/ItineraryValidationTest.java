package dev.jimador.bayes.itinerary;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jimador.bayes.decision.LocalItineraryDecisions;
import dev.jimador.bayes.provider.DayGuideTools;
import dev.jimador.bayes.provider.Place;
import dev.jimador.bayes.provider.Places;
import dev.jimador.bayes.provider.PublicPages;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ItineraryValidationTest {
    private static final LocalDate DATE = LocalDate.of(2030, 10, 15);
    private final Places places = mock(Places.class);
    private final PublicPages pages = mock(PublicPages.class);
    private final Places.Location location = new Places.Location("City", "America/New_York", 26, -80, 26.1, -80.1);
    private final DayGuideTools tools = new DayGuideTools(places, pages, location, List.of("seafood", "cuban"));
    private final ItineraryBrief brief = new ItineraryBrief("City", DATE, time("07:00"), time("14:00"),
            2, 0, "art", "steady", false, List.of("breakfast", "sight", "lunch"), List.of("seafood", "cuban"), List.of("italian"));
    private final ItineraryContext outing = new ItineraryContext(brief,
            new ItineraryMemories.Accepted("local", "art", "steady", List.of()), tools, "America/New_York", false);
    ItineraryValidationTest() {
        register("N1", "Breakfast Cafe", "breakfast", Map.of("breakfast", "yes", "opening_hours", "07:00-14:00"), "");
        register("N2", "Art Museum", "museum", Map.of("opening_hours", "10:00-17:00"), "");
        register("N3", "Seafood Place", "restaurant", Map.of("cuisine", "seafood", "opening_hours", "11:00-15:00"), "seafood");
        register("N4", "Cuban Place", "restaurant", Map.of("cuisine", "cuban", "opening_hours", "11:00-15:00"), "cuban");
        when(places.driveMinutes(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(5);
    }
    private void register(String id, String name, String category, Map<String, String> tags, String cuisine) {
        var place = new Place(id, name, category, "https://www.openstreetmap.org/node/" + id.substring(1), tags);
        when(places.search(location, category, cuisine)).thenReturn(List.of(new Places.MappedPlace(place, id.equals("N4") ? 26.4 : 26.2, id.equals("N4") ? -80.4 : -80.2)));
        tools.search(category, cuisine);
    }
    private ResearchFindings findings() {
        return new ResearchFindings(List.of(fact("N1", List.of(), List.of(), "07:00", "14:00"),
                fact("N2", List.of(), List.of(), "10:00", "17:00"),
                fact("N3", List.of("seafood"), List.of("lunch"), "11:00", "15:00"),
                fact("N4", List.of("cuban"), List.of("lunch"), "11:00", "15:00")));
    }
    private static ResearchFindings.PlaceFinding fact(String id, List<String> cuisines, List<String> meals, String open, String close) {
        return new ResearchFindings.PlaceFinding(id, "Attributed map interpretation", cuisines, meals, "open",
                List.of(new ResearchFindings.Window(DATE, time(open), time(close))), "unknown", "supported", List.of(id),
                List.of("Exact prices and menu can change; check routine opening updates."));
    }
    private static List<Itinerary.StopDraft> stops(String lunch) {
        return List.of(new Itinerary.StopDraft("breakfast", "N1", time("07:20"), time("08:00")),
                new Itinerary.StopDraft("sight", "N2", time("10:00"), time("11:00")),
                new Itinerary.StopDraft("lunch", lunch, time("11:20"), time("12:20")));
    }
    private Itinerary.Alternative check(List<Itinerary.StopDraft> stops, ResearchFindings research) {
        return ItineraryValidation.plans(new Itinerary.Draft(List.of(new Itinerary.AlternativeDraft("a", stops,
                "Fits the outing", List.of("Check exact menu and prices.")))), research, outing).alternatives().getFirst();
    }
    @Test void mappedBreakfastAndFittingWindowsAreUsableDespiteAdvisoryNotes() {
        var alternative = check(stops("N3"), new ResearchFindings(findings().places().subList(0, 3)));
        assertEquals("usable", alternative.status());
        assertTrue(alternative.openQuestions().isEmpty());
        assertEquals("mapped_breakfast_tag_unverified", alternative.stops().getFirst().mealServiceBasis());
        assertFalse(alternative.notes().isEmpty());
        assertEquals("Breakfast Cafe → Art Museum → Seafood Place", alternative.title());
        assertFalse(alternative.title().contains("Cuban"));
    }
    @Test void missingRequestedMealOrAnyStopTimeInvalidatesAnAlternative() {
        var missing = check(stops("N3").subList(1, 3), findings());
        assertEquals("invalid", missing.status());
        assertTrue(missing.openQuestions().contains("Missing requested breakfast stop."));
        var untimed = check(List.of(new Itinerary.StopDraft("breakfast", "N1", null, null),
                stops("N3").get(1), stops("N3").get(2)), findings());
        assertEquals("invalid", untimed.status());
        assertTrue(untimed.openQuestions().contains("Every stop needs a start and end time."));
    }
    @Test void genericCafeUnknownServiceIsAMaterialQuestion() {
        var generic = new Place("N5", "Generic Cafe", "cafe", "https://www.openstreetmap.org/node/5", Map.of("opening_hours", "07:00-14:00"));
        when(places.search(location, "cafe", "")).thenReturn(List.of(new Places.MappedPlace(generic, 26.2, -80.2)));
        tools.search("cafe", "");
        var facts = new ResearchFindings(java.util.stream.Stream.concat(findings().places().stream(),
                java.util.stream.Stream.of(fact("N5", List.of(), List.of("coffee"), "07:00", "14:00"))).toList());
        var proposed = List.of(new Itinerary.StopDraft("breakfast", "N5", time("07:20"), time("08:00")),
                stops("N3").get(1), stops("N3").get(2));
        var checked = check(proposed, facts);
        assertEquals("unresolved", checked.status());
        assertTrue(checked.openQuestions().stream().anyMatch(q -> q.contains("Meal service for breakfast")));
    }
    @Test void trustedTravelAndKnownClosuresAndWindowContradictionsInvalidatePlans() {
        when(places.driveMinutes(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(45);
        assertEquals("invalid", check(stops("N3"), findings()).status());
        var closed = new ResearchFindings.PlaceFinding("N2", "Closed on this date", List.of(), List.of(), "closed",
                List.of(), "unknown", "supported", List.of("N2"), List.of());
        var facts = new ResearchFindings(List.of(findings().places().get(0), closed, findings().places().get(2)));
        assertTrue(check(stops("N3"), facts).openQuestions().stream().anyMatch(q -> q.contains("reported closed")));
        var earlyMuseum = List.of(stops("N3").get(0), new Itinerary.StopDraft("sight", "N2", time("09:00"), time("09:45")), stops("N3").get(2));
        assertTrue(check(earlyMuseum, findings()).openQuestions().stream().anyMatch(q -> q.contains("operating window")));
    }
    @Test void capturedLateMuseumLegsReportEarliestArrivalsAndNeverRewriteTheDraft() {
        var mapped = List.of(
                new Places.MappedPlace(new Place("W532105760", "NSU Art Museum Fort Lauderdale", "museum",
                        "https://www.openstreetmap.org/way/532105760", Map.of("opening_hours", "Tu-Sa 11:00-17:00")), 26.7, -80.7),
                new Places.MappedPlace(new Place("N9796646021", "Padrino's Cuban Restaurant", "restaurant",
                        "https://www.openstreetmap.org/node/9796646021", Map.of("opening_hours", "Tu-Th 11:30-21:00", "cuisine", "cuban")), 26.4, -80.4),
                new Places.MappedPlace(new Place("N12922400401", "Mr. & Mrs. Crab Juicy Seafood and Bar", "restaurant",
                        "https://www.openstreetmap.org/node/12922400401", Map.of("opening_hours", "Su-Th 12:00-23:00", "cuisine", "seafood")), 26.6, -80.6));
        var capturedTools = new DayGuideTools(places, pages, location, List.of("seafood", "cuban"));
        for (var candidate : mapped) {
            String cuisine = candidate.evidence().tags().getOrDefault("cuisine", "");
            when(places.search(location, candidate.evidence().category(), cuisine)).thenReturn(List.of(candidate));
            capturedTools.search(candidate.evidence().category(), cuisine);
        }
        when(places.driveMinutes(26.4, -80.4, 26.7, -80.7)).thenReturn(8);
        when(places.driveMinutes(26.6, -80.6, 26.7, -80.7)).thenReturn(22);
        var profile = new ItineraryBrief("City", DATE, time("07:00"), time("14:00"), 2, 0, "art", "steady", false,
                List.of("lunch", "sight"), List.of("seafood", "cuban"), List.of());
        var context = new ItineraryContext(profile, outing.memory(), capturedTools, outing.timezone(), false);
        var research = new ResearchFindings(List.of(fact("W532105760", List.of(), List.of(), "11:00", "17:00"),
                fact("N9796646021", List.of("cuban"), List.of("lunch"), "11:30", "21:00"),
                fact("N12922400401", List.of("seafood"), List.of("lunch"), "12:00", "23:00")));
        var a = new Itinerary.AlternativeDraft("a", List.of(
                new Itinerary.StopDraft("lunch", "N9796646021", time("11:50"), time("12:50")),
                new Itinerary.StopDraft("sight", "W532105760", time("13:05"), time("14:00"))), "Captured Cuban leg", List.of());
        var b = new Itinerary.AlternativeDraft("b", List.of(
                new Itinerary.StopDraft("lunch", "N12922400401", time("12:10"), time("13:10")),
                new Itinerary.StopDraft("sight", "W532105760", time("13:25"), time("14:00"))), "Captured seafood leg", List.of());
        var checked = ItineraryValidation.plans(new Itinerary.Draft(List.of(a, b)), research, context);
        assertTrue(checked.alternatives().stream().allMatch(v -> v.status().equals("invalid")));
        assertEquals("Travel, parking and walking do not fit on leg N9796646021 -> W532105760 (NSU Art Museum Fort Lauderdale): departure 12:50 + trusted drive 8 minutes + 10-minute buffer = earliest "
                + DATE + "T13:08; requested start 13:05; shortfall 3 minutes.", checked.alternatives().getFirst().openQuestions().getFirst());
        assertEquals("Travel, parking and walking do not fit on leg N12922400401 -> W532105760 (NSU Art Museum Fort Lauderdale): departure 13:10 + trusted drive 22 minutes + 10-minute buffer = earliest "
                + DATE + "T13:42; requested start 13:25; shortfall 17 minutes.", checked.alternatives().getLast().openQuestions().getFirst());
        assertEquals(time("13:05"), a.stops().getLast().startsAt());
        assertEquals(time("13:25"), b.stops().getLast().startsAt());
        var fitting = List.of(new Itinerary.AlternativeDraft("a", List.of(a.stops().getFirst(),
                        new Itinerary.StopDraft("sight", "W532105760", time("13:08"), time("14:00"))), a.reason(), List.of()),
                new Itinerary.AlternativeDraft("b", List.of(b.stops().getFirst(),
                        new Itinerary.StopDraft("sight", "W532105760", time("13:42"), time("14:00"))), b.reason(), List.of()));
        assertTrue(ItineraryValidation.plans(new Itinerary.Draft(fitting), research, context).alternatives().stream()
                .allMatch(v -> v.status().equals("usable")));
    }
    @Test void differentLikedCuisinesAppearInHonestAlternativeTitlesAndTypedStops() {
        var result = ItineraryValidation.plans(new Itinerary.Draft(List.of(
                new Itinerary.AlternativeDraft("a", stops("N3"), "Seafood lunch", List.of()),
                new Itinerary.AlternativeDraft("b", stops("N4"), "Cuban lunch", List.of()))), findings(), outing);
        assertTrue(result.alternatives().stream().allMatch(a -> a.status().equals("usable")));
        assertEquals(List.of("seafood", "cuban"), result.alternatives().stream().flatMap(a -> a.stops().stream())
                .flatMap(s -> s.cuisines().stream()).distinct().toList());
        assertTrue(result.alternatives().get(1).title().endsWith("Cuban Place"));
    }
    @Test void duplicateOrSingleSeafoodDraftCannotClaimCoverageWhenCubanAlsoFits() {
        var seafood = new Itinerary.AlternativeDraft("a", stops("N3"), "Seafood lunch", List.of());
        var repeated = new Itinerary.AlternativeDraft("b", stops("N3"), "Another seafood lunch", List.of());
        var mapper = new ObjectMapper().findAndRegisterModules();
        for (var choices : List.of(List.of(seafood, repeated), List.of(seafood))) {
            var checked = ItineraryValidation.plans(new Itinerary.Draft(choices), findings(), outing);
            assertTrue(checked.alternatives().stream().allMatch(a -> a.status().equals("unresolved")));
            assertTrue(checked.alternatives().getFirst().openQuestions().stream()
                    .anyMatch(q -> q.contains("reachable liked cuisines: cuban")));
            assertEquals(1, checked.alternatives().size());
            assertFalse(checked.alternatives().getFirst().openQuestions().stream()
                    .anyMatch(q -> q.contains("Provide distinct alternatives")));
            var answer = ItineraryDecisions.evaluate(DecisionBrief.from(brief, checked.alternatives()),
                    checked.alternatives(), new LocalItineraryDecisions(mapper), mapper);
            assertEquals("false", answer.coverage());
            assertEquals("material-gaps", answer.classification());
        }
    }
    @Test void equivalentUsableAlternativesKeepTheFirstOriginalChoiceWithAnAdvisory() {
        var facts = new ResearchFindings(findings().places().subList(0, 3));
        var a = new Itinerary.AlternativeDraft("a", stops("N3"), "Original rationale", List.of());
        var b = new Itinerary.AlternativeDraft("b", stops("N3"), "Other rationale", List.of());
        var result = ItineraryValidation.plans(new Itinerary.Draft(List.of(a, b)), facts, outing);
        assertEquals(1, result.alternatives().size());
        var kept = result.alternatives().getFirst();
        assertEquals("a", kept.id());
        assertEquals("Original rationale", kept.reason());
        assertEquals("usable", kept.status());
        assertTrue(kept.openQuestions().isEmpty());
        assertTrue(kept.notes().contains("Equivalent venue/order alternative omitted."));
        assertEquals(check(a.stops(), facts).stops(), kept.stops());
    }
    @Test void anInvalidFirstScheduleCannotDisplaceAnEquivalentUsableLaterChoice() {
        var facts = new ResearchFindings(findings().places().subList(0, 3));
        var bad = new Itinerary.AlternativeDraft("a", List.of(
                new Itinerary.StopDraft("breakfast", "N1", time("07:05"), time("08:00")),
                stops("N3").get(1), stops("N3").get(2)), "Too little travel time", List.of());
        var good = new Itinerary.AlternativeDraft("b", stops("N3"), "Original useful choice", List.of());
        assertEquals("invalid", check(bad.stops(), facts).status());
        var kept = ItineraryValidation.plans(new Itinerary.Draft(List.of(bad, good)), facts, outing).alternatives();
        assertEquals(1, kept.size());
        assertEquals("b", kept.getFirst().id());
        assertEquals("usable", kept.getFirst().status());
        assertEquals("Original useful choice", kept.getFirst().reason());
        assertEquals(check(good.stops(), facts).stops(), kept.getFirst().stops());
    }
    @Test void deduplicationRetainsUnknownOperatingHoursAsAMaterialQuestion() {
        var unknown = new ResearchFindings.PlaceFinding("N3", "Hours not established", List.of("seafood"),
                List.of("lunch"), "unknown", List.of(), "unknown", "supported", List.of("N3"), List.of());
        var facts = new ResearchFindings(List.of(findings().place("N1"), findings().place("N2"), unknown));
        var result = ItineraryValidation.plans(new Itinerary.Draft(List.of(
                new Itinerary.AlternativeDraft("a", stops("N3"), "Original", List.of()),
                new Itinerary.AlternativeDraft("b", stops("N3"), "Repeated", List.of()))), facts, outing);
        assertEquals(1, result.alternatives().size());
        assertEquals("unresolved", result.alternatives().getFirst().status());
        assertEquals(List.of("Operating hours for Seafood Place are unknown."), result.alternatives().getFirst().openQuestions());
    }
    @Test void unknownSuitabilityCannotHideACuisineAcceptedByTheUsablePlanPolicy() {
        var cuban = findings().place("N4");
        var unknown = new ResearchFindings.PlaceFinding(cuban.placeId(), cuban.summary(), cuban.cuisines(),
                cuban.supportedMeals(), cuban.hoursStatus(), cuban.operatingWindows(), cuban.access(),
                "unknown", cuban.sourceIds(), cuban.notes());
        var facts = new ResearchFindings(List.of(findings().place("N1"), findings().place("N2"),
                findings().place("N3"), unknown));
        var seafoodChoice = new Itinerary.AlternativeDraft("a", stops("N3"), "Seafood lunch", List.of());
        var cubanChoice = new Itinerary.AlternativeDraft("b", stops("N4"), "Cuban lunch", List.of());
        var control = ItineraryValidation.plans(new Itinerary.Draft(List.of(seafoodChoice, cubanChoice)), facts, outing);
        assertTrue(control.alternatives().stream().allMatch(a -> a.status().equals("usable")));
        var omitted = ItineraryValidation.plans(new Itinerary.Draft(List.of(seafoodChoice)), facts, outing);
        assertEquals("unresolved", omitted.alternatives().getFirst().status());
        assertTrue(omitted.alternatives().getFirst().openQuestions().stream().anyMatch(q -> q.contains("liked cuisines: cuban")));
        var mapper = new ObjectMapper().findAndRegisterModules();
        var answer = ItineraryDecisions.evaluate(DecisionBrief.from(brief, omitted.alternatives()),
                omitted.alternatives(), new LocalItineraryDecisions(mapper), mapper);
        assertEquals("false", answer.coverage());
        assertEquals("material-gaps", answer.classification());
    }
    @Test void omittedCuisineIsRequiredOnlyWithFittingEvidenceAndTrustedReachability() {
        var lateCuban = fact("N4", List.of("cuban"), List.of("lunch"), "13:00", "14:00");
        var late = new ResearchFindings(List.of(findings().places().get(0), findings().places().get(1),
                findings().places().get(2), lateCuban));
        assertEquals("usable", check(stops("N3"), late).status());
        when(places.driveMinutes(26.2, -80.2, 26.4, -80.4)).thenReturn(null);
        assertEquals("usable", check(stops("N3"), findings()).status());
    }
    @Test void inventedResearchSourcesBecomeExplicitlyUnknown() {
        var invented = new ResearchFindings.PlaceFinding("N1", "Hours", List.of(), List.of(), "open",
                List.of(new ResearchFindings.Window(DATE, time("07:00"), time("14:00"))), "unknown", "supported",
                List.of("invented-site"), List.of());
        var normalized = ItineraryValidation.research(new ResearchFindings(List.of(invented)), List.of("N1"), outing).place("N1");
        assertTrue(normalized.sourceIds().isEmpty());
        assertTrue(normalized.operatingWindows().isEmpty());
        assertEquals("unknown", normalized.hoursStatus());
        assertDoesNotThrow(() -> ItineraryValidation.research(findings(), List.of("N1", "N2", "N3", "N4"), outing));
    }
    private static LocalTime time(String value) { return LocalTime.parse(value); }
}
