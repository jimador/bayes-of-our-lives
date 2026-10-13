package dev.jimador.bays.web;

import dev.jimador.bays.decision.BayesDecisionServices;
import dev.jimador.bays.guide.BayesGuideService;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BayesWebTest {
    private static final String URL = "/api/guide";
    private static final String QUERY = """
            {"mode":"live","city":"Miami, Florida","intent":"sights","mealPeriod":"lunch",
             "availableMinutes":90,"profile":"art-quiet","engine":"local"}
            """;

    @Test void validatesRealCityRequestAndExcludesFixtureMode() {
        var valid = new BayesGuideService.GuideQuery("live", "Miami, Florida", "sights", "lunch", 90,
                "art-quiet", "local");
        assertDoesNotThrow(valid::validate);
        assertThrows(IllegalArgumentException.class, () -> new BayesGuideService.GuideQuery("fixed", "Harborview",
                "sights", "lunch", 90, "art-quiet", "local").validate());
        assertThrows(IllegalArgumentException.class, () -> new BayesGuideService.GuideQuery("live", "",
                "sights", "lunch", 90, "art-quiet", "local").validate());
        assertThrows(IllegalArgumentException.class, () -> new BayesGuideService.GuideQuery("live", "Miami",
                "meal", "brunch", 90, "art-quiet", "local").validate());
        assertThrows(IllegalArgumentException.class, () -> new BayesGuideService.GuideQuery("live", "Miami",
                "sights", "lunch", 90, "art-quiet", "clef").validate());
    }

    @Test void apiSeparatesValidEmptyResultFromEngineAndLookupFailures() throws Exception {
        var service = mock(BayesGuideService.class);
        var mvc = MockMvcBuilders.standaloneSetup(new BayesGuideController(service)).build();
        when(service.guide(any())).thenReturn(new BayesGuideService.GuideResult("no_result", null,
                "Miami, United States", "live", "meal", "dinner", "local", "public search", "2026-10-12T00:00:00Z",
                10, "context=bays-fictional-art-quiet", List.of(), List.of(), List.of()));
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(QUERY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.outcome").value("no_result"));
        doThrow(new BayesDecisionServices.ModelUnavailableException("Key is not configured")).when(service).guide(any());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(QUERY))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.code").value("MODEL_UNAVAILABLE"));
        doThrow(new BayesGuideService.DecisionResponseException()).when(service).guide(any());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(QUERY))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("DECISION_FAILED"));
        doThrow(new IllegalArgumentException("Choose a listed decision engine")).when(service).guide(any());
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(QUERY))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        doThrow(new IllegalStateException("secret host-path")).when(service).guide(any());
        var response = mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(QUERY))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("GUIDE_UNAVAILABLE"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("secret"));
        assertFalse(response.contains("host-path"));
    }
}
