package dev.jimador.bayes.provider;

import java.net.URI;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OfficialPageResearchTest {
    @Test void rejectsNonPublicOrUnencryptedWebsiteTags() {
        assertFalse(OfficialPageResearch.safePublicHttps(URI.create("http://example.org/visit")));
        assertFalse(OfficialPageResearch.safePublicHttps(URI.create("https://localhost/visit")));
        assertFalse(OfficialPageResearch.safePublicHttps(URI.create("https://127.0.0.1/visit")));
    }
    @Test void keepsResearchAsAttributedCluesNotVerifiedHours() {
        String html = "<html><title>Visit the Museum</title><script>fake hours</script>"
                + "<p>Opening hours can change; check current tickets and accessibility before traveling.</p></html>";
        assertEquals("Visit the Museum", OfficialPageResearch.extractTitle(html));
        assertTrue(OfficialPageResearch.clues(html).stream().anyMatch(s -> s.contains("Opening hours")));
        assertFalse(OfficialPageResearch.clues(html).stream().anyMatch(s -> s.contains("fake hours")));
    }
    @Test void keepsExplicitBreakfastCluesWithoutTreatingCoffeeAsFullService() {
        String html = "<p>Breakfast served at 7am. Coffee and brunch listed on our menu.</p>";
        assertTrue(OfficialPageResearch.clues(html).stream().anyMatch(s -> s.contains("Breakfast served at 7am")));
        assertTrue(OfficialPageResearch.clues(html).stream().anyMatch(s -> s.contains("Coffee and brunch")));
    }
}
