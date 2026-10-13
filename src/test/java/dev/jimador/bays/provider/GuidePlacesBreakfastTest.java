package dev.jimador.bays.provider;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuidePlacesBreakfastTest {
    @Test void breakfastSearchIsLocalAndMealSpecific() {
        URI url = URI.create(GuidePlaces.breakfastSearchUrl(26.12, -80.14));
        String query = URLDecoder.decode(url.getRawQuery(), StandardCharsets.UTF_8);
        assertEquals("photon.komoot.io", url.getHost());
        assertTrue(query.contains("q=breakfast"));
        assertTrue(query.contains("lat=26.12&lon=-80.14"));
        assertTrue(query.contains("bbox="));
        assertTrue(query.contains("osm_tag=amenity:restaurant"));
        assertTrue(query.contains("osm_tag=amenity:cafe"));
    }
    @Test void lookupTagsAreBoundToTheMappedOsmId() throws Exception {
        var details = new ObjectMapper().readTree("""
                [{"osm_type":"way","osm_id":551457772,
                  "extratags":{"cuisine":"breakfast;coffee;lunch",
                               "opening_hours":"Mo-Fr 07:00-13:00"}},
                 {"osm_type":"way","osm_id":"bad","extratags":{"cuisine":"breakfast"}}]
                """);
        var tags = GuidePlaces.breakfastTags(details);
        assertEquals("breakfast;coffee;lunch", tags.get("https://www.openstreetmap.org/way/551457772")
                .get("cuisine"));
        assertEquals("Mo-Fr 07:00-13:00", tags.get("https://www.openstreetmap.org/way/551457772")
                .get("opening_hours"));
        assertEquals(1, tags.size());
    }
}
