package dev.jimador.bays.web;

import dev.jimador.bays.decision.BayesDecisionServices;
import dev.jimador.bays.itinerary.BayesItineraryService;
import dev.jimador.bays.itinerary.ItineraryRequest;

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
        var mvc = MockMvcBuilders.standaloneSetup(new BayesItineraryController(service)).build();
        doThrow(new BayesDecisionServices.ModelUnavailableException("synthetic unavailable"))
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

}
