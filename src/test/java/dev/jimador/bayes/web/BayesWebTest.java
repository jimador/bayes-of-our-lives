package dev.jimador.bayes.web;

import dev.jimador.bayes.itinerary.BayesItineraryService;
import dev.jimador.bayes.itinerary.ItineraryRequest;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BayesWebTest {
    @Test void itineraryApiBindsCuisineFieldsAndKeepsOlderPayloadCompatible() throws Exception {
        var service = mock(BayesItineraryService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new BayesItineraryController(service, mock(BayesItineraryService.class), mock(BayesItineraryService.class))).build();
        doThrow(new BayesItineraryService.ModelUnavailableException("synthetic unavailable"))
                .when(service).plan(any());
        String base = """
                {"city":"Fort Lauderdale","startingLocation":"Synthetic airport area",
                 "date":"2030-10-15","startTime":"07:00","endTime":"14:00",
                 "breakfast":true,"coffee":false,"earlyFoodDeclined":false,"lunch":true,"dinner":false,
                 "partySize":2,"childrenCount":0,"interests":"art","pace":"steady","stepFreeAccess":false,
                 "engine":"local","publicMapConsent":true,"remoteModelConsent":true}
                """;
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

}
