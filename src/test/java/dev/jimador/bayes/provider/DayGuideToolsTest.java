package dev.jimador.bayes.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DayGuideToolsTest {
    @Test void originAndLookupCoordinatesStayLocalAndRoutesAreCachedAndBounded() throws Exception {
        Places places = mock(Places.class);
        PublicPages pages = mock(PublicPages.class);
        var location = new Places.Location("City", "America/New_York", 26, -80, 26.123456, -80.654321);
        var place = new Place("N7", "Breakfast", "breakfast", "https://www.openstreetmap.org/node/7",
                Map.of("breakfast", "yes", "website", "https://example.com/?origin=private"));
        when(places.search(location, "breakfast", "")).thenReturn(List.of(new Places.MappedPlace(place, 26.2, -80.2)));
        when(places.driveMinutes(26.123456, -80.654321, 26.2, -80.2)).thenReturn(12);
        var tools = new DayGuideTools(places, pages, location, List.of("cuban"));
        String json = new ObjectMapper().writeValueAsString(tools.search("breakfast", ""));
        assertFalse(json.contains("26.123456"));
        assertFalse(json.contains("origin=private"));
        assertEquals(12, tools.route("origin", "N7").driveMinutes());
        assertEquals(12, tools.route("origin", "N7").driveMinutes());
        verify(places, times(1)).driveMinutes(26.123456, -80.654321, 26.2, -80.2);
        assertThrows(IllegalArgumentException.class, () -> tools.route("origin", "N999"));
        assertThrows(IllegalArgumentException.class, () -> tools.search("restaurant", "private medical text"));
        for (int i = 0; i < 6; i++) {
            String category = List.of("museum", "park", "cafe", "restaurant", "breakfast", "cafe").get(i);
            String cuisine = i > 3 ? "cuban" : "";
            when(places.search(location, category, cuisine)).thenReturn(List.of());
            tools.search(category, cuisine);
        }
        assertThrows(IllegalStateException.class, () -> tools.search("restaurant", "cuban"));
    }
    @Test void sourcesBelongToTheExactRetrievedPlace() {
        Places places = mock(Places.class);
        PublicPages pages = mock(PublicPages.class);
        var location = new Places.Location("City", "UTC", 0, 0, 0, 0);
        var place = new Place("N7", "Place", "museum", "https://www.openstreetmap.org/node/7", Map.of());
        when(places.search(location, "museum", "")).thenReturn(List.of(new Places.MappedPlace(place, 0, 0)));
        when(pages.read(place, "City")).thenReturn(List.of(new PageEvidence("N7-site", "N7", "https://example.com", "retrieved_unverified", "hours")));
        var tools = new DayGuideTools(places, pages, location, List.of());
        tools.search("museum", "");
        assertFalse(tools.hasSource("N7", "N7-site"));
        tools.readPlace("N7");
        assertTrue(tools.hasSource("N7", "N7-site"));
        assertFalse(tools.hasSource("N8", "N7-site"));
    }
}
