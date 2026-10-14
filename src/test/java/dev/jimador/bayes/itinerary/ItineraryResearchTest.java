package dev.jimador.bayes.itinerary;

import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.core.support.InvalidLlmReturnFormatException;
import com.embabel.common.ai.model.LlmOptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jimador.bayes.provider.DayGuideTools;
import dev.jimador.bayes.provider.PageEvidence;
import dev.jimador.bayes.provider.Place;
import dev.jimador.bayes.provider.Places;
import dev.jimador.bayes.provider.PublicPages;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

class ItineraryResearchTest {
    private static final LocalDate DATE = LocalDate.of(2030, 10, 15);
    private final Places places = mock(Places.class);
    private final PublicPages pages = mock(PublicPages.class);
    private final Places.Location location = new Places.Location("City", "America/New_York", 26, -80, 26.123456, -80.654321);
    private final DayGuideTools tools = new DayGuideTools(places, pages, location, List.of());
    private final ItineraryContext outing = new ItineraryContext(new ItineraryBrief("City", DATE, time("10:00"), time("14:00"),
            2, 0, "art", "steady", false, List.of("sight"), List.of(), List.of()),
            new ItineraryMemories.Accepted("local", "art", "steady", List.of()), tools, "America/New_York", false);
    ItineraryResearchTest() {
        when(places.driveMinutes(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(5);
        for (int i = 1; i <= 3; i++) {
            String id = "N" + i;
            String category = List.of("museum", "restaurant", "cafe").get(i - 1);
            var place = new Place(id, "Place " + i, category, "https://www.openstreetmap.org/node/" + i,
                    Map.of("opening_hours", "10:00-14:00"));
            when(places.search(location, category, "")).thenReturn(List.of(new Places.MappedPlace(place, 26.2, -80.2)));
            when(pages.read(place, "City")).thenReturn(List.of(new PageEvidence(id + "-site", id, "https://example.com/" + i,
                    "retrieved_unverified", "Public venue hours and meal evidence")));
            tools.search(category, "");
        }
    }
    private static ResearchFindings.Window window() { return new ResearchFindings.Window(DATE, time("10:00"), time("14:00")); }
    private static ResearchFindings.PlaceFinding positive(String id, List<String> sources, String hours, List<ResearchFindings.Window> windows) {
        return new ResearchFindings.PlaceFinding(id, "Sourced interpretation", List.of("seafood"), List.of("lunch"), hours,
                windows, "supported", "supported", sources, List.of("Prices are advisory."));
    }
    @Test void invalidOrEmptyCitationsDropAllClaimsWithoutBorrowingMappedProvenance() {
        var findings = new ResearchFindings(List.of(positive("N1", List.of("N1", "invented-site"), "open", List.of(window())),
                positive("N2", List.of(), "open", List.of(window()))));
        var normalized = ItineraryValidation.research(findings, List.of("N1", "N2"), outing);
        for (var fact : normalized.places()) {
            assertTrue(fact.sourceIds().isEmpty());
            assertTrue(fact.cuisines().isEmpty());
            assertTrue(fact.supportedMeals().isEmpty());
            assertTrue(fact.operatingWindows().isEmpty());
            assertEquals("unknown", fact.hoursStatus());
            assertEquals("unknown", fact.access());
            assertEquals("unknown", fact.suitability());
            assertTrue(fact.notes().getFirst().contains("INVALID_SOURCES"));
            assertFalse(fact.summary().contains("Sourced interpretation"));
            assertFalse(fact.notes().contains("Prices are advisory."));
        }
        assertEquals("https://www.openstreetmap.org/node/1", tools.evidence("N1").place().sourceUrl());
        assertTrue(tools.hasSource("N1", "N1"));
        assertEquals("unresolved", ItineraryValidation.plans(draft(), normalized, outing).alternatives().getFirst().status());
    }
    @Test void selectedIdsRemainExactWithMissingDuplicateForeignAndMalformedFindings() {
        var malformed = new ResearchFindings.PlaceFinding("N2", null, null, List.of("INVALID_MEAL"), "INVALID_HOURS",
                List.of(window()), null, "INVALID_SUITABILITY", List.of("N2"), null);
        var findings = new ResearchFindings(List.of(positive("N1", List.of("N1"), "open", List.of(window())),
                positive("N1", List.of("N1"), "open", List.of(window())), positive("N99", List.of("N99"), "open", List.of(window())), malformed));
        var normalized = ItineraryValidation.research(findings, List.of("N1", "N2", "N3"), outing);
        assertEquals(List.of("N1", "N2", "N3"), normalized.places().stream().map(ResearchFindings.PlaceFinding::placeId).toList());
        assertTrue(normalized.place("N1").notes().getFirst().contains("DUPLICATE_PLACE"));
        assertTrue(normalized.place("N3").notes().getFirst().contains("MISSING_PLACE"));
        assertTrue(normalized.place("N2").supportedMeals().isEmpty());
        assertTrue(normalized.place("N2").operatingWindows().isEmpty());
        assertEquals("unknown", normalized.place("N2").suitability());
        assertEquals(List.of("N2"), normalized.place("N2").sourceIds());
    }
    @Test void validFactsRetainTheirExactRetrievedPageSourceAndOnlyCoherentWindows() {
        tools.readPlace("N1");
        var windows = new ArrayList<ResearchFindings.Window>();
        windows.add(window());
        windows.add(new ResearchFindings.Window(DATE.plusDays(1), time("10:00"), time("14:00")));
        windows.add(new ResearchFindings.Window(DATE, time("14:00"), time("10:00")));
        windows.add(null);
        var normalized = ItineraryValidation.research(new ResearchFindings(List.of(positive("N1", List.of("N1-site"), "open", windows))), List.of("N1"), outing).place("N1");
        assertEquals(List.of("N1-site"), normalized.sourceIds());
        assertEquals(List.of(window()), normalized.operatingWindows());
        assertEquals(List.of("lunch"), normalized.supportedMeals());
        assertEquals("open", normalized.hoursStatus());
        assertTrue(normalized.notes().stream().anyMatch(n -> n.contains("INVALID_WINDOWS")));
        assertFalse(normalized.sourceIds().contains("N1"));
    }
    @Test void unknownOrMalformedHoursCannotTurnProposedWindowsIntoUsableEvidence() {
        for (String status : List.of("unknown", "MALFORMED")) {
            var normalized = ItineraryValidation.research(new ResearchFindings(List.of(positive("N1", List.of("N1"), status, List.of(window())))), List.of("N1"), outing);
            assertEquals("unknown", normalized.place("N1").hoursStatus());
            assertTrue(normalized.place("N1").operatingWindows().isEmpty());
            var checked = ItineraryValidation.plans(draft(), normalized, outing).alternatives().getFirst();
            assertEquals("unresolved", checked.status());
            assertTrue(checked.openQuestions().contains("Operating hours for Place 1 are unknown."));
        }
    }
    @Test void onlyKnownBindingFailureBecomesDisclosedUnknownResearch() throws Exception {
        var model = LlmOptions.withModel("research-fixture");
        var mapper = new ObjectMapper().findAndRegisterModules();
        var agent = new BayesAgent(mock(ItineraryMemories.class), places, pages, mapper, model, model);
        OperationContext context = mock(OperationContext.class, RETURNS_DEEP_STUBS);
        var runner = context.ai().withLlm(model).withToolObject(tools);
        var binding = new InvalidLlmReturnFormatException("PRIVATE_MODEL_SENTINEL", ResearchFindings.class,
                new IllegalArgumentException("PRIVATE_EXCEPTION_SENTINEL"));
        when(runner.createObject(anyString(), eq(ResearchFindings.class))).thenThrow(binding);
        var discovery = new BayesAgent.Discovery(List.of("N1", "N2"), "Fixture");
        var unknown = agent.researchPlaces(outing, discovery, context);
        assertEquals(List.of("N1", "N2"), unknown.places().stream().map(ResearchFindings.PlaceFinding::placeId).toList());
        assertTrue(unknown.places().stream().allMatch(p -> p.sourceIds().isEmpty() && p.supportedMeals().isEmpty()
                && p.operatingWindows().isEmpty() && p.notes().getFirst().contains("MODEL_OUTPUT_FORMAT")));
        assertFalse(mapper.writeValueAsString(unknown).contains("PRIVATE_"));
        var providerFailure = new IllegalStateException("PRIVATE_PROVIDER_SENTINEL");
        when(runner.createObject(anyString(), eq(ResearchFindings.class))).thenThrow(providerFailure);
        assertSame(providerFailure, assertThrows(IllegalStateException.class, () -> agent.researchPlaces(outing, discovery, context)));
    }
    private static Itinerary.Draft draft() {
        return new Itinerary.Draft(List.of(new Itinerary.AlternativeDraft("a", List.of(new Itinerary.StopDraft("sight", "N1",
                time("10:20"), time("11:00"))), "Fixture outline", List.of())));
    }
    private static LocalTime time(String value) { return LocalTime.parse(value); }
}
