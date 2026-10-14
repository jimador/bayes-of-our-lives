package dev.jimador.bayes.itinerary;

import com.embabel.common.ai.decision.DecisionService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.embabel.agent.core.AgentPlatform;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import dev.jimador.bayes.provider.GuidePlaces;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TripperConsentTest {
    @TempDir Path temporary;
    @Test void noRemoteConsentReturnsQuestionBeforeAgentOrProviderRuns() {
        var platform = mock(AgentPlatform.class);
        var decisions = mock(DecisionService.class);
        var service = new BayesItineraryService(platform, decisions,
                new ItineraryMemories(temporary.resolve("preferences.json")));
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
        when(places.searchCategory(anyString(), anyString(), anyString(), anyList())).thenReturn(
                new GuidePlaces.Search("Fort Lauderdale", "synthetic source", Instant.now(),
                        List.of(), "America/New_York"));
        var request = new ItineraryRequest("Fort Lauderdale", "Synthetic airport area", LocalDate.now().plusDays(7),
                LocalTime.of(7, 0), LocalTime.of(14, 0), true, false, false,
                true, false, 2, 0, "art", "steady", false, "synthetic allergy marker", "local", true, true,
                null, false, false, "Brazilian", "Italian");
        new ItineraryResearch(places).findAndResearch(request);
        verify(places, times(3)).searchCategory(eq("Fort Lauderdale"), eq("Synthetic airport area"), anyString());
        verify(places).searchCategory("Fort Lauderdale", "Synthetic airport area", "restaurant", List.of("brazilian"));
        verify(places).searchCategory("Fort Lauderdale", "Synthetic airport area", "breakfast");
        verifyNoMoreInteractions(places);
    }
    @Test void selectedDecisionTravelsAsTypedInputToTheSameAgent() {
        var platform = mock(AgentPlatform.class);
        var agent = mock(com.embabel.agent.core.Agent.class);
        var process = mock(com.embabel.agent.core.AgentProcess.class);
        var decision = mock(DecisionService.class);
        when(agent.getName()).thenReturn("BayesAgent");
        when(platform.agents()).thenReturn(List.of(agent));
        when(platform.createAgentProcessFrom(eq(agent), any(com.embabel.agent.core.ProcessOptions.class),
                any(ItineraryRequest.class), any(BayesAgent.SelectedDecision.class))).thenReturn(process);
        when(process.run()).thenReturn(process);
        var service = new BayesItineraryService(platform, decision,
                new ItineraryMemories(temporary.resolve("preferences.json")));
        var request = new ItineraryRequest("Miami", "Downtown", LocalDate.of(2030, 10, 15),
                LocalTime.of(10, 0), LocalTime.of(14, 0), false, false, false,
                false, false, 2, 0, "art", "steady", false, "", "local", true, true);
        assertThrows(BayesItineraryService.ItineraryUnavailableException.class, () -> service.plan(request));
        verify(platform).createAgentProcessFrom(eq(agent), any(com.embabel.agent.core.ProcessOptions.class),
                eq(request), argThat(value -> value instanceof BayesAgent.SelectedDecision selected
                        && selected.service() == decision));
        verifyNoInteractions(decision);
    }

}
