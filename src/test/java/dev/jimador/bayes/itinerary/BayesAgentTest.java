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
        var evidence = agent.retrieveEvidence(outing, discovered);
        var researched = agent.researchPlaces(outing, evidence, context);
        var drafted = agent.draftDay(outing, researched, evidence, context);
        var reviewed = agent.reviewDraft(outing, researched, drafted);
        var planned = agent.finalizeDay(outing, researched, evidence, reviewed, context);
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
        var evidenceProducers = metadata.getActions().stream().filter(a -> a.getOutputs().stream()
                .anyMatch(b -> b.getValue().endsWith(":" + BayesAgent.RetrievedEvidence.class.getName()))).toList();
        assertEquals(1, evidenceProducers.size());
        assertTrue(evidenceProducers.getFirst().getName().contains("retrieveEvidence"));
        assertTrue(evidenceProducers.getFirst().getInputs().stream()
                .anyMatch(b -> b.getValue().endsWith(":" + BayesAgent.Discovery.class.getName())));
        for (String action : List.of("researchPlaces", "draftDay", "finalizeDay")) {
            var consumer = metadata.getActions().stream().filter(a -> a.getName().contains(action)).findFirst().orElseThrow();
            assertTrue(consumer.getInputs().stream()
                    .anyMatch(b -> b.getValue().endsWith(":" + BayesAgent.RetrievedEvidence.class.getName())));
        }
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
        var evidence = agent.retrieveEvidence(outing, new BayesAgent.Discovery(List.of("N7"), "Art"));
        var bad = new Itinerary.Draft(List.of(new Itinerary.AlternativeDraft("a", List.of(new Itinerary.StopDraft("sight", "N7",
                java.time.LocalTime.of(10, 20), java.time.LocalTime.of(11, 0))), "Draft", List.of())));
        var good = new Itinerary.Draft(List.of(new Itinerary.AlternativeDraft("a", List.of(new Itinerary.StopDraft("sight", "N7",
                java.time.LocalTime.of(10, 33), java.time.LocalTime.of(11, 0))), "Draft", List.of())));
        OperationContext context = mock(OperationContext.class, RETURNS_DEEP_STUBS);
        var runner = context.ai().withLlm(planner).withToolObject(tools);
        when(runner.createObject(anyString(), eq(Itinerary.Draft.class))).thenReturn(bad, good);
        var reviewed = agent.reviewDraft(outing, research, agent.draftDay(outing, research, evidence, context));
        assertEquals("invalid", reviewed.alternatives().getFirst().status());
        assertEquals(23, reviewed.alternatives().getFirst().stops().getFirst().baseTravelMinutes());
        assertEquals("usable", agent.finalizeDay(outing, research, evidence, reviewed, context).alternatives().getFirst().status());
        var prompts = ArgumentCaptor.forClass(String.class);
        verify(runner, times(2)).createObject(prompts.capture(), eq(Itinerary.Draft.class));
        assertTrue(prompts.getAllValues().getLast().contains("\"baseTravelMinutes\":23"));
        var noCall = mock(OperationContext.class);
        var validReview = agent.reviewDraft(outing, research, good);
        assertEquals("usable", agent.finalizeDay(outing, research, evidence, validReview, noCall).alternatives().getFirst().status());
        verifyNoInteractions(noCall);
        var ignored = mock(OperationContext.class, RETURNS_DEEP_STUBS);
        var ignoredRunner = ignored.ai().withLlm(planner).withToolObject(tools);
        when(ignoredRunner.createObject(anyString(), eq(Itinerary.Draft.class))).thenReturn(bad);
        assertEquals("invalid", agent.finalizeDay(outing, research, evidence, reviewed, ignored).alternatives().getFirst().status());
        verify(ignoredRunner).createObject(anyString(), eq(Itinerary.Draft.class));
    }
    @Test void capturedMappedIdentityAndRetrievedPagesReachEveryModelStageOnce() throws Exception {
        // Mapped fields from final-local/final-jev captures; their selected pages arrays were all empty.
        var captured = List.of(
                new Place("W532105760", "NSU Art Museum Fort Lauderdale", "museum", "https://www.openstreetmap.org/way/532105760",
                        Map.of("fee", "yes", "opening_hours", "Tu-Sa 11:00-17:00; Su 12:00-17:00")),
                new Place("W49508471", "Hugh Taylor Birch State Park", "park", "https://www.openstreetmap.org/way/49508471",
                        Map.of("opening_hours", "Mo-Su 08:00-20:30")),
                new Place("N12922400401", "Mr. & Mrs. Crab Juicy Seafood and Bar", "restaurant", "https://www.openstreetmap.org/node/12922400401",
                        Map.of("opening_hours", "Su-Th 12:00-23:00; Fr-Sa 12:00-24:00", "cuisine", "seafood")),
                new Place("N14202541907", "Mario's Catalina Cuban Restaurant", "restaurant", "https://www.openstreetmap.org/node/14202541907",
                        Map.of("opening_hours", "Mo-Su 11:00-22:00", "cuisine", "cuban")));
        Places places = mock(Places.class);
        PublicPages pages = mock(PublicPages.class);
        var location = new Places.Location("City", "America/New_York", 26, -80, 26.123456, -80.654321);
        when(places.locate("City", "PRIVATE_ORIGIN_SENTINEL")).thenReturn(location);
        when(places.driveMinutes(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenReturn(5);
        var researchOptions = LlmOptions.withModel("research-fixture");
        var planOptions = LlmOptions.withModel("plan-fixture");
        var agent = new BayesAgent(new ItineraryMemories(temporary.resolve("captured-preferences.json")),
                places, pages, mapper, researchOptions, planOptions);
        var prepared = agent.prepare(request());
        var outing = new ItineraryContext(prepared.brief(), prepared.memory(),
                new dev.jimador.bayes.provider.DayGuideTools(places, pages, location, List.of("seafood", "cuban")),
                prepared.timezone(), prepared.privateRestrictionCheck());
        for (var place : captured) {
            String cuisine = place.tags().getOrDefault("cuisine", "");
            when(places.search(location, place.category(), cuisine)).thenReturn(List.of(new Places.MappedPlace(place, 26.2, -80.2)));
            when(pages.read(place, "City")).thenReturn(List.of(new dev.jimador.bayes.provider.PageEvidence(
                    place.id() + "-site", place.id(), "https://example.test/" + place.id(), "retrieved_unverified", "Untrusted fixture page for " + place.name())));
            outing.tools().search(place.category(), cuisine);
            assertTrue(outing.tools().evidence(place.id()).pages().isEmpty());
        }
        var discovery = new BayesAgent.Discovery(captured.stream().map(Place::id).toList(), "Captured selection");
        var evidence = agent.retrieveEvidence(outing, discovery);
        var cached = agent.retrieveEvidence(outing, discovery);
        assertEquals(evidence, cached);
        for (var place : captured) verify(pages, times(1)).read(place, "City");
        var findings = ResearchFindings.unavailable(discovery.placeIds(), ResearchFindings.Issue.MISSING_PLACE);
        var draft = new Itinerary.Draft(List.of(new Itinerary.AlternativeDraft("a", List.of(new Itinerary.StopDraft(
                "sight", "W532105760", LocalTime.of(11, 0), LocalTime.NOON)), "Unknown material facts", List.of())));
        var context = mock(OperationContext.class, RETURNS_DEEP_STUBS);
        var researcher = context.ai().withLlm(researchOptions).withToolObject(outing.tools());
        var planner = context.ai().withLlm(planOptions).withToolObject(outing.tools());
        when(researcher.createObject(anyString(), eq(ResearchFindings.class))).thenReturn(findings);
        when(planner.createObject(anyString(), eq(Itinerary.Draft.class))).thenReturn(draft);
        var research = agent.researchPlaces(outing, evidence, context);
        var proposed = agent.draftDay(outing, research, evidence, context);
        var finalPlan = agent.finalizeDay(outing, research, evidence, agent.reviewDraft(outing, research, proposed), context);
        assertEquals("unresolved", finalPlan.alternatives().getFirst().status());
        assertEquals("unknown", research.places().getFirst().hoursStatus());
        var prompts = ArgumentCaptor.forClass(String.class);
        verify(researcher).createObject(prompts.capture(), eq(ResearchFindings.class));
        verify(planner, times(2)).createObject(prompts.capture(), eq(Itinerary.Draft.class));
        for (String prompt : prompts.getAllValues()) {
            String marker = "Retrieved venue evidence JSON: ";
            var supplied = mapper.readValue(prompt.substring(prompt.indexOf(marker) + marker.length()), BayesAgent.RetrievedEvidence.class);
            assertEquals(captured, supplied.places().stream().map(e -> e.place()).toList());
            assertEquals(evidence, supplied);
            for (var place : captured) {
                assertTrue(prompt.contains(place.id() + "-site"));
                assertTrue(prompt.contains("Untrusted fixture page for " + place.name()));
            }
            assertFalse(prompt.contains("PRIVATE_ORIGIN_SENTINEL"));
            assertFalse(prompt.contains("PRIVATE_MEDICAL_SENTINEL"));
            assertFalse(prompt.contains("26.123456"));
            assertFalse(prompt.contains("-80.654321"));
            assertFalse(prompt.contains("nominatim.openstreetmap.org/search"));
        }
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
