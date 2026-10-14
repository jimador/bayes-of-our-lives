package dev.jimador.bayes.web;

import dev.jimador.bayes.itinerary.BayesItineraryService;
import dev.jimador.bayes.itinerary.ItineraryRequest;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class BayesWebTest {
    private static final String BASE = """
                {"city":"Fort Lauderdale","startingLocation":"Synthetic airport area",
                 "date":"2030-10-15","startTime":"07:00","endTime":"14:00",
                 "breakfast":true,"coffee":false,"earlyFoodDeclined":false,"lunch":true,"dinner":false,
                 "partySize":2,"childrenCount":0,"interests":"art","pace":"steady","stepFreeAccess":false,
                 "engine":"local","publicMapConsent":true,"remoteModelConsent":true}
                """;

    @Test void itineraryApiBindsCuisineFieldsAndKeepsOlderPayloadCompatible() throws Exception {
        var service = mock(BayesItineraryService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new BayesItineraryController(service, mock(BayesItineraryService.class), mock(BayesItineraryService.class))).build();
        doThrow(new BayesItineraryService.ModelUnavailableException("synthetic unavailable"))
                .when(service).plan(any());
        String base = BASE;
        String enriched = base.replace("\"engine\":", "\"cuisineLikes\":\" Brazilian \",\"cuisineDislikes\":\"Italian\",\"engine\":");
        mvc.perform(post("/api/itinerary").contentType(MediaType.APPLICATION_JSON).content(enriched))
                .andExpect(status().isServiceUnavailable());
        mvc.perform(post("/api/itinerary").contentType(MediaType.APPLICATION_JSON).content(base))
                .andExpect(status().isServiceUnavailable());
        var request = org.mockito.ArgumentCaptor.forClass(ItineraryRequest.class);
        verify(service, times(2)).plan(request.capture());
        assertEquals("brazilian", request.getAllValues().getFirst().cuisineLikes());
        assertEquals("italian", request.getAllValues().getFirst().cuisineDislikes());
        assertDoesNotThrow(request.getAllValues().getFirst()::validateShape);
        assertEquals("", request.getAllValues().getLast().cuisineLikes());
    }

    @Test void unreadableRequestsReturnSafeBadRequestBeforeAnyServiceCall() throws Exception {
        var local = mock(BayesItineraryService.class);
        var jev = mock(BayesItineraryService.class);
        var openai = mock(BayesItineraryService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new BayesItineraryController(local, jev, openai)).build();
        for (String body : java.util.List.of("{\"engine\":\"invalid\"}", "{}", "null", "{\"city\":",
                "{\"partySize\":\"PRIVATE_MEDICAL_SENTINEL\",\"startingLocation\":\"PRIVATE_ORIGIN_SENTINEL\"}")) {
            var result = mvc.perform(post("/api/itinerary").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                    .andExpect(jsonPath("$.message").value("Send a complete itinerary request with valid JSON field values."))
                    .andReturn();
            String response = result.getResponse().getContentAsString();
            assertFalse(response.contains("PRIVATE_MEDICAL_SENTINEL"));
            assertFalse(response.contains("PRIVATE_ORIGIN_SENTINEL"));
        }
        verifyNoInteractions(local, jev, openai);
    }
    @Test void dispatchesExactlyOneServiceForEachEngineAndRejectsUnsupportedEngine() {
        var local = mock(BayesItineraryService.class);
        var jev = mock(BayesItineraryService.class);
        var openai = mock(BayesItineraryService.class);
        var controller = new BayesItineraryController(local, jev, openai);
        var request = new ItineraryRequest("Miami", "Downtown", java.time.LocalDate.of(2030, 10, 15),
                java.time.LocalTime.of(10, 0), java.time.LocalTime.of(14, 0), false, false, false,
                false, false, 2, 0, "art", "steady", false, "", "local", true, true);
        for (var engine : java.util.List.of("local", "jev", "openai")) {
            var selected = request.withEngine(engine);
            controller.itinerary(selected);
            var expected = switch (engine) { case "jev" -> jev; case "openai" -> openai; default -> local; };
            verify(expected).plan(selected);
            verifyNoMoreInteractions(local, jev, openai);
            clearInvocations(local, jev, openai);
        }
        assertThrows(IllegalArgumentException.class, () -> controller.itinerary(request.withEngine("unknown")));
        verifyNoInteractions(local, jev, openai);
    }
    @Test void invalidModelSelectionIsSafeGatewayFailureWhileBadEngineIsBadRequest() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
        var options = com.embabel.common.ai.model.LlmOptions.withModel("research-fixture");
        var agent = new dev.jimador.bayes.itinerary.BayesAgent(null, null, null, mapper, options, options);
        var context = mock(com.embabel.agent.api.common.OperationContext.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        org.mockito.Mockito.when(context.ai().withLlm(options).createObject(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(dev.jimador.bayes.itinerary.BayesAgent.Discovery.class)))
                .thenReturn(new dev.jimador.bayes.itinerary.BayesAgent.Discovery(java.util.List.of("N999_PRIVATE_PROVIDER_SENTINEL"), "Invented"));
        var brief = new dev.jimador.bayes.itinerary.ItineraryBrief("City", java.time.LocalDate.of(2030, 10, 15),
                java.time.LocalTime.of(7, 0), java.time.LocalTime.of(14, 0), 2, 0, "art", "steady", false,
                java.util.List.of("sight"), java.util.List.of(), java.util.List.of());
        var outing = new dev.jimador.bayes.itinerary.ItineraryContext(brief, null, null, "America/New_York", false);
        var candidates = new dev.jimador.bayes.itinerary.BayesAgent.MappedCandidates(java.util.List.of(
                new dev.jimador.bayes.provider.Place("N7", "Mapped Museum", "museum", "https://www.openstreetmap.org/node/7", java.util.Map.of())));
        var failure = assertThrows(IllegalStateException.class, () -> agent.discoverPlaces(outing, candidates, context));
        var service = mock(BayesItineraryService.class);
        doThrow(failure).when(service).plan(any());
        var mvc = MockMvcBuilders.standaloneSetup(new BayesItineraryController(mock(BayesItineraryService.class), service, mock(BayesItineraryService.class))).build();
        var response = mvc.perform(post("/api/itinerary").contentType(MediaType.APPLICATION_JSON)
                        .content(BASE.replace("\"local\"", "\"jev\"")))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("RESEARCH_UNAVAILABLE"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("PRIVATE_PROVIDER_SENTINEL"));
        assertFalse(response.contains("Unknown mapped place ID"));
        mvc.perform(post("/api/itinerary").contentType(MediaType.APPLICATION_JSON).content(BASE.replace("\"local\"", "\"unknown\"")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        verify(service).plan(any());
    }

}
