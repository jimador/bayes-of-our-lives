package dev.jimador.bays.itinerary;

import dev.jimador.bays.decision.BayesDecisionServices;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.embabel.agent.core.AgentPlatform;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import dev.jimador.bays.provider.GuidePlaces;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TripperConsentTest {
    @TempDir Path temporary;
    @Test void noRemoteConsentReturnsQuestionBeforeAgentOrProviderRuns() {
        var platform = mock(AgentPlatform.class);
        var decisions = mock(BayesDecisionServices.class);
        var service = new BayesItineraryService(platform, decisions,
                new ItineraryMemory(temporary.resolve("preferences.json")));
        var request = new ItineraryRequest("Fort Lauderdale", "Synthetic airport area", LocalDate.now().plusDays(7),
                LocalTime.of(10, 0), LocalTime.of(16, 0), false, false, false,
                true, false, 2, 0, "art", "steady", false, "synthetic allergy marker", "local", true, false,
                null, false, false, "Brazilian", "Italian");
        var result = service.plan(request);
        assertEquals("needs_input", result.status());
        assertTrue(result.questions().getFirst().contains("OpenAI"));
        verifyNoInteractions(platform, decisions);
        assertTrue(result.questions().getFirst().contains("cuisine likes/dislikes"));
    }

    @Test void mapLookupReceivesOnlyLocationAndCategoryNotDietaryText() {
        var places = mock(GuidePlaces.class);
        when(places.searchCategory(anyString(), anyString(), anyString())).thenReturn(
                new GuidePlaces.Search("Fort Lauderdale", "synthetic source", Instant.now(),
                        List.of(), "America/New_York"));
        var request = new ItineraryRequest("Fort Lauderdale", "Synthetic airport area", LocalDate.now().plusDays(7),
                LocalTime.of(7, 0), LocalTime.of(14, 0), true, false, false,
                true, false, 2, 0, "art", "steady", false, "synthetic allergy marker", "local", true, true,
                null, false, false, "Brazilian", "Italian");
        new ItineraryResearch(places).findAndResearch(request);
        verify(places, times(4)).searchCategory(eq("Fort Lauderdale"), eq("Synthetic airport area"), anyString());
        verify(places).searchCategory("Fort Lauderdale", "Synthetic airport area", "breakfast");
        verifyNoMoreInteractions(places);
    }
}
