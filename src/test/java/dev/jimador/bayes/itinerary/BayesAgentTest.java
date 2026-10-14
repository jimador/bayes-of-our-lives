package dev.jimador.bayes.itinerary;

import com.embabel.agent.api.annotation.support.AgentMetadataReader;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.api.common.PromptRunner;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.AgentProcessStatusCode;
import com.embabel.agent.core.ProcessOptions;
import com.embabel.common.ai.model.LlmOptions;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jimador.bayes.decision.LocalItineraryDecisions;
import dev.jimador.bayes.provider.Place;
import dev.jimador.bayes.provider.Places;
import dev.jimador.bayes.provider.PublicPages;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;

class BayesAgentTest {
    @TempDir Path temporary;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private static ItineraryRequest request() {
        return new ItineraryRequest("City", "PRIVATE_ORIGIN_SENTINEL", LocalDate.of(2030, 10, 15),
                LocalTime.of(10, 0), LocalTime.of(14, 0), false, false, true, false, false,
                2, 0, "art", "steady", false, "PRIVATE_MEDICAL_SENTINEL", "local", true, true);
    }
    @Test void typedActionsAttachActualToolsAndConfiguredModelsWithoutPrivateRequestFields() {
        Places places = mock(Places.class);
        PublicPages pages = mock(PublicPages.class);
        var location = new Places.Location("City", "America/New_York", 26, -80, 26.123456, -80.654321);
        when(places.locate("City", "PRIVATE_ORIGIN_SENTINEL")).thenReturn(location);
        var place = new Place("N7", "Art Museum", "museum", "https://www.openstreetmap.org/node/7", Map.of("opening_hours", "10:00-14:00"));
        when(places.search(location, "museum", "")).thenReturn(List.of(new Places.MappedPlace(place, 26.2, -80.2)));
        when(places.driveMinutes(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(5);
        var researcher = LlmOptions.withModel("configured-researcher");
        var planner = LlmOptions.withModel("configured-planner");
        var agent = new BayesAgent(new ItineraryMemories(temporary.resolve("preferences.json")), places, pages,
                mapper, researcher, planner);
        var outing = agent.prepare(request());
        outing.tools().search("museum", "");
        OperationContext context = mock(OperationContext.class, RETURNS_DEEP_STUBS);
        var researchRunner = context.ai().withLlm(researcher).withToolObject(outing.tools());
        var placeRunner = context.ai().withLlm(planner).withToolObject(outing.tools());
        var planningRunner = mock(PromptRunner.class);
        when(placeRunner.withToolObject(any(ItineraryPlanningTools.class))).thenReturn(planningRunner);
        var discovery = new BayesAgent.Discovery(List.of("N7"), "Art interest");
        var findings = new ResearchFindings(List.of(new ResearchFindings.PlaceFinding("N7", "Map hours interpretation",
                List.of(), List.of(), "open", List.of(new ResearchFindings.Window(request().date(),
                LocalTime.of(10, 0), LocalTime.of(14, 0))), "unknown", "supported", List.of("N7"), List.of())));
        var draft = new Itinerary.Draft(List.of(new Itinerary.AlternativeDraft("a", List.of(new Itinerary.StopDraft("sight", "N7",
                LocalTime.of(10, 20), LocalTime.of(11, 0))), "Art outing", List.of())));
        when(researchRunner.createObject(anyString(), eq(BayesAgent.Discovery.class))).thenReturn(discovery);
        when(researchRunner.createObject(anyString(), eq(ResearchFindings.class))).thenReturn(findings);
        when(planningRunner.createObject(anyString(), eq(Itinerary.Draft.class))).thenReturn(draft);
        var discovered = agent.discoverPlaces(outing, context);
        var researched = agent.researchPlaces(outing, discovered, context);
        var planned = agent.planDay(outing, researched, context);
        assertEquals("unresolved", planned.alternatives().getFirst().status());
        assertEquals("Art Museum", planned.alternatives().getFirst().title());
        var feedback = ArgumentCaptor.forClass(ItineraryPlanningTools.class);
        verify(placeRunner).withToolObject(feedback.capture());
        assertEquals(planned, feedback.getValue().review(draft));
        var prompts = ArgumentCaptor.forClass(String.class);
        verify(researchRunner).createObject(prompts.capture(), eq(BayesAgent.Discovery.class));
        verify(researchRunner).createObject(prompts.capture(), eq(ResearchFindings.class));
        verify(planningRunner).createObject(prompts.capture(), eq(Itinerary.Draft.class));
        for (String prompt : prompts.getAllValues()) {
            assertFalse(prompt.contains("PRIVATE_ORIGIN_SENTINEL"));
            assertFalse(prompt.contains("PRIVATE_MEDICAL_SENTINEL"));
            assertFalse(prompt.contains("26.123456"));
            assertFalse(prompt.contains("-80.654321"));
            assertTrue(prompt.contains("art"));
        }
        var review = agent.decideDay(outing, planned, new BayesAgent.SelectedDecision(new LocalItineraryDecisions(mapper)));
        var result = agent.completeItinerary(outing, researched, planned, review);
        assertEquals("Art Museum", result.researchedPlaces().getFirst().place().name());
        assertEquals("https://www.openstreetmap.org/node/7", result.researchedPlaces().getFirst().place().sourceUrl());
    }
    @Test void rawRequestNeverEntersTheKnownAgentProcessBlackboard() {
        AgentPlatform platform = mock(AgentPlatform.class);
        AgentMetadataReader reader = mock(AgentMetadataReader.class);
        BayesAgent bayes = mock(BayesAgent.class);
        var knownAgent = mock(com.embabel.agent.core.Agent.class);
        when(reader.createAgentMetadata(bayes)).thenReturn(knownAgent);
        var memory = new ItineraryMemories(temporary.resolve("preferences.json"));
        var brief = ItineraryBrief.from(request(), memory.rememberForThisOuting(request()));
        var tools = new dev.jimador.bayes.provider.DayGuideTools(mock(Places.class), mock(PublicPages.class),
                new Places.Location("City", "America/New_York", 26, -80, 26.123456, -80.654321), List.of());
        var outing = new ItineraryContext(brief, new ItineraryMemories.Accepted("local", "art", "steady", List.of()),
                tools, "America/New_York", true);
        when(bayes.prepare(any())).thenReturn(outing);
        AgentProcess process = mock(AgentProcess.class);
        when(platform.createAgentProcessFrom(eq(knownAgent), any(ProcessOptions.class), eq(outing), any(BayesAgent.SelectedDecision.class))).thenReturn(process);
        when(process.run()).thenReturn(process);
        when(process.getStatus()).thenReturn(AgentProcessStatusCode.COMPLETED);
        var service = new BayesItineraryService(platform, new LocalItineraryDecisions(mapper), memory, bayes, reader);
        assertThrows(BayesItineraryService.ItineraryUnavailableException.class, () -> service.plan(request()));
        var invocation = mockingDetails(platform).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("createAgentProcessFrom")).findFirst().orElseThrow();
        assertSame(knownAgent, invocation.getArgument(0));
        assertSame(outing, invocation.getArgument(2));
        for (Object input : invocation.getArguments()) {
            assertFalse(input instanceof ItineraryRequest);
            assertFalse(String.valueOf(input).contains("PRIVATE_ORIGIN_SENTINEL"));
            assertFalse(String.valueOf(input).contains("PRIVATE_MEDICAL_SENTINEL"));
            assertFalse(String.valueOf(input).contains("26.123456"));
        }
        verify(platform, never()).agents();
    }
}
