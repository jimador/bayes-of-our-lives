package dev.jimador.bayes.itinerary;

import com.embabel.agent.api.tool.Tool;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ItineraryPlanningToolsTest {
    private static final LocalDate DATE = LocalDate.of(2030, 10, 15);
    private final Places places = mock(Places.class);
    private final Places.Location location = new Places.Location("City", "America/New_York", 26, -80, 26.123456, -80.654321);
    private final DayGuideTools routes = new DayGuideTools(places, mock(PublicPages.class), location, List.of());
    private final ItineraryBrief brief = new ItineraryBrief("City", DATE, time("10:00"), time("14:00"), 2, 0,
            "art", "steady", false, List.of("sight", "lunch"), List.of(), List.of());
    private final ResearchFindings research = new ResearchFindings(List.of(fact("N7", List.of()), fact("N8", List.of("lunch"))));
    private final ItineraryPlanningTools feedback;

    ItineraryPlanningToolsTest() {
        register("N7", "Museum", "museum", 26.2, -80.2);
        register("N8", "Restaurant", "restaurant", 26.3, -80.3);
        when(places.driveMinutes(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(5);
        when(places.driveMinutes(26.2, -80.2, 26.3, -80.3)).thenReturn(23);
        feedback = new ItineraryPlanningTools(new ItineraryContext(brief,
                new ItineraryMemories.Accepted("local", "art", "steady", List.of()), routes, "America/New_York", false), research);
    }
    private void register(String id, String name, String category, double lat, double lon) {
        Place place = new Place(id, name, category, "https://www.openstreetmap.org/node/" + id.substring(1), Map.of("opening_hours", "10:00-14:00"));
        when(places.search(location, category, "")).thenReturn(List.of(new Places.MappedPlace(place, lat, lon)));
        routes.search(category, "");
    }
    private static ResearchFindings.PlaceFinding fact(String id, List<String> meals) {
        return new ResearchFindings.PlaceFinding(id, "Attributed operating window", List.of(), meals, "open",
                List.of(new ResearchFindings.Window(DATE, time("10:00"), time("14:00"))), "unknown", "unknown", List.of(id), List.of());
    }
    private static Itinerary.Draft draft(String lunchStarts) {
        return new Itinerary.Draft(List.of(new Itinerary.AlternativeDraft("a", List.of(
                new Itinerary.StopDraft("sight", "N7", time("10:20"), time("12:10")),
                new Itinerary.StopDraft("lunch", "N8", time(lunchStarts), time("13:20"))), "Museum and lunch", List.of())));
    }
    @Test void registeredTypedToolReturnsActualTravelFeedbackAndCorrectedDraftPasses() throws Exception {
        var tools = Tool.fromInstance(feedback);
        assertEquals(1, tools.size());
        var tool = tools.getFirst();
        assertEquals("review", tool.getDefinition().getName());
        assertTrue(tool.getDefinition().getInputSchema().toJsonSchema().contains("draft"));
        var mapper = new ObjectMapper().findAndRegisterModules().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        var result = assertInstanceOf(Tool.Result.WithArtifact.class, tool.call(mapper.writeValueAsString(Map.of("draft", draft("12:30")))));
        var invalid = assertInstanceOf(Itinerary.class, result.getArtifact()).alternatives().getFirst();
        assertEquals("invalid", invalid.status());
        assertEquals(23, invalid.stops().getLast().baseTravelMinutes());
        assertTrue(invalid.openQuestions().contains("Travel, parking and walking do not fit before a stop."));
        assertFalse(result.getContent().contains("26.123456"));
        assertFalse(result.getContent().contains("-80.654321"));
        var corrected = assertInstanceOf(Tool.Result.WithArtifact.class, tool.call(mapper.writeValueAsString(Map.of("draft", draft("12:43")))));
        assertEquals("usable", assertInstanceOf(Itinerary.class, corrected.getArtifact()).alternatives().getFirst().status());
    }
    @Test void reviewCallsAreBoundedAtThree() {
        for (int i = 0; i < 3; i++) assertEquals("usable", feedback.review(draft("12:43")).alternatives().getFirst().status());
        assertThrows(IllegalStateException.class, () -> feedback.review(draft("12:43")));
    }
    private static LocalTime time(String value) { return LocalTime.parse(value); }
}
