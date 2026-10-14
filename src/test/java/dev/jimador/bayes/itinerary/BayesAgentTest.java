package dev.jimador.bayes.itinerary;

import com.embabel.agent.api.annotation.support.AgentMetadataReader;
import com.embabel.agent.api.common.OperationContext;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
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
        var planningRunner = context.ai().withLlm(planner).withToolObject(outing.tools());
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
        var drafted = agent.draftDay(outing, researched, context);
        var reviewed = agent.reviewDraft(outing, researched, drafted);
        var planned = agent.finalizeDay(outing, researched, reviewed, context);
        assertEquals("unresolved", planned.alternatives().getFirst().status());
        assertEquals("Art Museum", planned.alternatives().getFirst().title());
        var prompts = ArgumentCaptor.forClass(String.class);
        verify(researchRunner).createObject(prompts.capture(), eq(BayesAgent.Discovery.class));
        verify(researchRunner).createObject(prompts.capture(), eq(ResearchFindings.class));
        verify(planningRunner, times(2)).createObject(prompts.capture(), eq(Itinerary.Draft.class));
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
    @Test void metadataRequiresDraftReviewAndOnlyFinalizationProducesItinerary() {
        var agent = new BayesAgent(mock(ItineraryMemories.class), mock(Places.class), mock(PublicPages.class), mapper,
                LlmOptions.withModel("research-fixture"), LlmOptions.withModel("plan-fixture"));
        var metadata = assertInstanceOf(com.embabel.agent.core.Agent.class, new AgentMetadataReader().createAgentMetadata(agent));
        var producers = metadata.getActions().stream().filter(a -> a.getOutputs().stream()
                .anyMatch(binding -> binding.getValue().endsWith(":" + Itinerary.class.getName()))).toList();
        assertEquals(1, producers.size());
        assertTrue(producers.getFirst().getName().contains("finalizeDay"));
        assertTrue(producers.getFirst().getInputs().stream().anyMatch(b -> b.getValue().endsWith(":" + Itinerary.ReviewedDraft.class.getName())));
        var reviewer = metadata.getActions().stream().filter(a -> a.getName().contains("reviewDraft")).findFirst().orElseThrow();
        assertTrue(reviewer.getInputs().stream().anyMatch(b -> b.getValue().endsWith(":" + Itinerary.Draft.class.getName())));
        assertTrue(reviewer.getOutputs().stream().anyMatch(b -> b.getValue().endsWith(":" + Itinerary.ReviewedDraft.class.getName())));
    }
    @Test void refinementConsumesActualReviewOnceAndFinalValidationRunsForBothBranches() {
        Places places = mock(Places.class);
        PublicPages pages = mock(PublicPages.class);
        var location = new Places.Location("City", "America/New_York", 26, -80, 26.1, -80.1);
        var tools = new dev.jimador.bayes.provider.DayGuideTools(places, pages, location, List.of());
        var place = new Place("N7", "Art Museum", "museum", "https://www.openstreetmap.org/node/7", Map.of("opening_hours", "10:00-14:00"));
        when(places.search(location, "museum", "")).thenReturn(List.of(new Places.MappedPlace(place, 26.2, -80.2)));
        when(places.driveMinutes(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(23);
        tools.search("museum", "");
        var brief = new ItineraryBrief("City", request().date(), java.time.LocalTime.of(10, 0), java.time.LocalTime.of(14, 0),
                2, 0, "art", "steady", false, List.of("sight"), List.of(), List.of());
        var outing = new ItineraryContext(brief, new ItineraryMemories.Accepted("local", "art", "steady", List.of()), tools, "America/New_York", false);
        var research = new ResearchFindings(List.of(new ResearchFindings.PlaceFinding("N7", "Attributed map hours", List.of(), List.of(), "open",
                List.of(new ResearchFindings.Window(request().date(), java.time.LocalTime.of(10, 0), java.time.LocalTime.of(14, 0))),
                "unknown", "unknown", List.of("N7"), List.of())));
        var planner = LlmOptions.withModel("plan-fixture");
        var agent = new BayesAgent(mock(ItineraryMemories.class), places, pages, mapper, planner, planner);
        var bad = new Itinerary.Draft(List.of(new Itinerary.AlternativeDraft("a", List.of(new Itinerary.StopDraft("sight", "N7",
                java.time.LocalTime.of(10, 20), java.time.LocalTime.of(11, 0))), "Draft", List.of())));
        var good = new Itinerary.Draft(List.of(new Itinerary.AlternativeDraft("a", List.of(new Itinerary.StopDraft("sight", "N7",
                java.time.LocalTime.of(10, 33), java.time.LocalTime.of(11, 0))), "Draft", List.of())));
        OperationContext context = mock(OperationContext.class, RETURNS_DEEP_STUBS);
        var runner = context.ai().withLlm(planner).withToolObject(tools);
        when(runner.createObject(anyString(), eq(Itinerary.Draft.class))).thenReturn(bad, good);
        var reviewed = agent.reviewDraft(outing, research, agent.draftDay(outing, research, context));
        assertEquals("invalid", reviewed.alternatives().getFirst().status());
        assertEquals(23, reviewed.alternatives().getFirst().stops().getFirst().baseTravelMinutes());
        assertEquals("usable", agent.finalizeDay(outing, research, reviewed, context).alternatives().getFirst().status());
        var prompts = ArgumentCaptor.forClass(String.class);
        verify(runner, times(2)).createObject(prompts.capture(), eq(Itinerary.Draft.class));
        assertTrue(prompts.getAllValues().getLast().contains("\"baseTravelMinutes\":23"));
        var noCall = mock(OperationContext.class);
        var validReview = agent.reviewDraft(outing, research, good);
        assertEquals("usable", agent.finalizeDay(outing, research, validReview, noCall).alternatives().getFirst().status());
        verifyNoInteractions(noCall);
        var ignored = mock(OperationContext.class, RETURNS_DEEP_STUBS);
        var ignoredRunner = ignored.ai().withLlm(planner).withToolObject(tools);
        when(ignoredRunner.createObject(anyString(), eq(Itinerary.Draft.class))).thenReturn(bad);
        assertEquals("invalid", agent.finalizeDay(outing, research, reviewed, ignored).alternatives().getFirst().status());
        verify(ignoredRunner).createObject(anyString(), eq(Itinerary.Draft.class));
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
