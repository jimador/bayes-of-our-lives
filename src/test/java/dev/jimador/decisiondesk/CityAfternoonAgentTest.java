package dev.jimador.decisiondesk;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class CityAfternoonAgentTest {
    private static CityAfternoonAgent.Assessment assessment(String name, int drive, String choice) {
        var place = new CityOutingProvider.Place(name, 25.7, -80.2, "https://www.openstreetmap.org/node/1");
        var option = new CityOutingProvider.Option(place, 27.0, 10.0, 4.0,
                Instant.parse("2026-10-11T18:00:00Z"), "https://api.open-meteo.com/", drive);
        return new CityAfternoonAgent.Assessment(option, "candidate", "true", choice, "easy", "nearby", "test", 1, 1);
    }

    @Test void decisionAnswersControlEligibilityAndScoresOrderResearchLeads() {
        var agent = new CityAfternoonAgent();
        var assessments = new CityAfternoonAgent.Assessments("Miami", "geo", Instant.parse("2026-10-11T18:00:00Z"),
                Instant.parse("2026-10-10T18:00:00Z"), List.of(assessment("far", 24, "candidate"),
                assessment("close", 4, "candidate"), assessment("excluded", 1, "review")));
        var prefs = new CityAfternoonAgent.Preferences(new PaddleComparisonAgent.PreferenceProfile(
                "test", "intracoastal", 0, 0.01, 0.01));
        var ranked = agent.rank(assessments, prefs);
        assertEquals(List.of("close", "far"), ranked.names());
        assertEquals("close", ranked.first());
    }
}
