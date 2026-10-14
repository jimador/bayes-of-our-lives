package dev.jimador.bayes.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlacesTest {
    @Test void providerDtosBindActualSchemasAndIgnoreUnrelatedFields() throws Exception {
        var mapper = new ObjectMapper();
        var geo = mapper.readValue("{\"results\":[{\"name\":\"City\",\"latitude\":26.1,\"longitude\":-80.2,\"timezone\":\"America/New_York\",\"country\":\"United States\",\"elevation\":2}]}", Places.Geocoding.class);
        assertEquals("America/New_York", geo.results().getFirst().timezone());
        var map = mapper.readValue("[{\"osm_type\":\"node\",\"osm_id\":7,\"lat\":\"26.1\",\"lon\":\"-80.2\",\"extratags\":{\"breakfast\":\"yes\"},\"licence\":\"OSM\"}]", Places.MapHit[].class);
        assertEquals("7", map[0].osmId());
        assertEquals("yes", map[0].extratags().get("breakfast"));
        var photon = mapper.readValue("{\"features\":[{\"geometry\":{\"coordinates\":[-80.2,26.1]},\"properties\":{\"osm_type\":\"N\",\"osm_id\":7,\"name\":\"Cafe\"}}]}", Places.Photon.class);
        assertEquals("N", photon.features().getFirst().properties().osmType());
        assertEquals(26.1, photon.features().getFirst().geometry().coordinates().get(1));
        var route = mapper.readValue("{\"code\":\"Ok\",\"routes\":[{\"duration\":601,\"distance\":3000}]}", Places.Routing.class);
        assertEquals(601, route.routes().getFirst().duration());
        assertFalse(Places.safeTags(Map.of("phone", "private", "opening_hours", "07:00-15:00")).containsKey("phone"));
    }
}
