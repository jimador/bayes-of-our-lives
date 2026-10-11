package dev.jimador.decisiondesk;

import static org.junit.jupiter.api.Assertions.*;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcessStatusCode;
import com.embabel.agent.core.ProcessOptions;
import com.embabel.common.ai.decision.QuestionKind;
import com.embabel.common.ai.classification.ModelProvenance;
import com.embabel.common.ai.decision.LevelProbability;
import com.embabel.common.ai.decision.RatingResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "demo.engine=local", "demo.warmups=0", "demo.runs=1", "demo.trace=false",
        "embabel.agent.platform.models.openai.api-key=unused-local-mode"
})
class PaddleComparisonAgentTest {
    @Autowired AgentPlatform platform;

    @Test void projectsDistributionOnlyRatingsOnlyWithAMajority() {
        var model = new ModelProvenance("jev-1.13.0", "TypeSafe");
        var majority = new RatingResult.Answered(model, null,
                List.of(new LevelProbability("easy", 0.83), new LevelProbability("unknown", 0.12),
                        new LevelProbability("hard", 0.05)));
        assertEquals("easy", PaddleComparisonAgent.ratingProjection(majority));
        var noMajority = new RatingResult.Answered(model, null,
                List.of(new LevelProbability("easy", 0.45), new LevelProbability("unknown", 0.35),
                        new LevelProbability("hard", 0.20)));
        assertEquals("no-majority-level", PaddleComparisonAgent.ratingProjection(noMajority));
    }

    private PaddleDayAgent.Conditions readings(String id, Double wind, Double current) {
        return new PaddleDayAgent.Conditions(id, "2026-10-10T12:00:00Z", "2026-10-10T19:00:00Z",
                wind, 10.0, 1.0, false, 3.0, "rising", current);
    }

    private PaddleComparisonAgent.CompareRequest request(String city, int firstDelay, int secondDelay,
                                                         Double thirdCurrent, boolean missingSecondWind) {
        var places = SyntheticCityCatalog.places(city);
        return new PaddleComparisonAgent.CompareRequest(city, LocalDate.of(2026, 10, 10), "intracoastal",
                Instant.parse("2026-10-10T14:00:00Z"), Instant.parse("2026-10-10T14:00:00Z"),
                Instant.parse("2026-10-10T16:00:00Z"), Instant.parse("2026-10-10T18:00:00Z"),
                Instant.parse("2026-10-10T17:00:00Z"), Instant.parse("2026-10-10T21:00:00Z"),
                List.of(new PaddleComparisonAgent.RampInput(places.get(0).waterway(), readings("demo-a", 9.0, 0.6), firstDelay),
                        new PaddleComparisonAgent.RampInput(places.get(1).waterway(), readings("demo-b", missingSecondWind ? null : 7.0, 0.4), secondDelay),
                        new PaddleComparisonAgent.RampInput(places.get(2).waterway(), readings("demo-c", 8.0, thirdCurrent), 20)));
    }

    private PaddleComparisonAgent.CompareRequest request(int firstDelay, int secondDelay,
                                                         Double thirdCurrent, boolean missingSecondWind) {
        return request("charleston", firstDelay, secondDelay, thirdCurrent, missingSecondWind);
    }

    private PaddleComparisonAgent.ComparisonCard run(PaddleComparisonAgent.CompareRequest request) {
        var agent = platform.agents().stream().filter(a -> a.getName().contains("PaddleComparisonAgent"))
                .findFirst().orElseThrow();
        var process = platform.createAgentProcessFrom(agent, new ProcessOptions(), request).run();
        assertEquals(AgentProcessStatusCode.COMPLETED, process.getStatus(), () -> process.getFailureInfo().toString());
        var names = process.getHistory().stream().map(h -> h.getActionName().split("\\.")[4]).toList();
        assertTrue(names.indexOf("gather") < names.indexOf("assess"), names.toString());
        assertTrue(names.indexOf("assess") < names.indexOf("rank"), names.toString());
        assertTrue(names.indexOf("preferences") < names.indexOf("rank"), names.toString());
        assertTrue(names.indexOf("rank") < names.indexOf("finish"), names.toString());
        return process.last(PaddleComparisonAgent.ComparisonCard.class);
    }

    @Test void typedRequestsAndRankingSelectTheRampInTheGoalCard() {
        assertEquals(java.util.EnumSet.allOf(QuestionKind.class), new PreferenceDecisionService().capabilities().getQuestionKinds());
        var first = run(request(10, 30, 0.3, false));
        assertEquals("Charleston Demo Intracoastal A", first.destination());
        assertEquals("charleston", first.city());
        assertEquals(Instant.parse("2026-10-10T16:00:00Z"), first.windowStartUtc());
        assertEquals(Instant.parse("2026-10-10T18:00:00Z"), first.windowEndUtc());
        assertEquals("candidate", first.status());
        assertEquals("request-default", first.profile().source());
        assertEquals(first.destination(), first.rankedOptions().getFirst());
        assertEquals(3, first.assessments().size());
        assertEquals("true", first.assessments().getFirst().proposition());
        assertEquals("candidate", first.assessments().getFirst().choice());
        assertEquals("easy", first.assessments().getFirst().rating());
        assertEquals("intracoastal", first.assessments().getFirst().classification());
        assertEquals("ocean", first.assessments().get(1).classification());

        var delayed = run(request(91, 30, 1.5, false));
        assertEquals("Charleston Demo Ocean B", delayed.destination());
        assertEquals(Instant.parse("2026-10-10T17:00:00Z"), delayed.windowStartUtc());
        assertEquals(delayed.destination(), delayed.rankedOptions().getFirst());
    }

    @Test void missingAndIneligibleReadingsNeverForceAWinner() {
        var review = run(request(91, 91, null, true));
        assertNull(review.destination());
        assertEquals("review", review.status());
        assertTrue(review.rankedOptions().isEmpty());

        var defer = run(request(91, 181, 1.5, false));
        assertNull(defer.destination());
        assertEquals("defer", defer.status());
        assertTrue(defer.rankedOptions().isEmpty());
    }

    @Test void changingTideHeightChangesTheSelectedPlace() {
        var tied = run(request(20, 24, 1.5, false));
        assertEquals("Charleston Demo Intracoastal A", tied.destination());
        assertEquals(2, tied.rankedOptions().size());

        var lowWaterAtHorlbeck = request(20, 24, 1.5, false);
        var original = lowWaterAtHorlbeck.ramps().getFirst();
        var c = original.conditions();
        var lower = new PaddleDayAgent.Conditions(c.id(), c.forecastIssuedUtc(), c.forecastValidUntilUtc(),
                c.windKt(), c.gustKt(), c.swellFt(), c.stormSignal(), 1.5, c.tidePhase(), c.tidalCurrentKt());
        var changed = new PaddleComparisonAgent.CompareRequest(lowWaterAtHorlbeck.city(), lowWaterAtHorlbeck.localDate(),
                lowWaterAtHorlbeck.preferredWaterwayType(), lowWaterAtHorlbeck.asOfUtc(),
                lowWaterAtHorlbeck.departureUtc(), lowWaterAtHorlbeck.windowStartUtc(),
                lowWaterAtHorlbeck.windowEndUtc(), lowWaterAtHorlbeck.predictionTimeUtc(),
                lowWaterAtHorlbeck.syntheticDaylightEndUtc(),
                List.of(new PaddleComparisonAgent.RampInput(original.waterway(), lower, original.trafficDelayMinutes()),
                        lowWaterAtHorlbeck.ramps().get(1), lowWaterAtHorlbeck.ramps().get(2)));
        assertEquals("Charleston Demo Ocean B", run(changed).destination());
    }

    @Test void emptyInputProducesNoWinner() {
        var baseline = request(10, 30, 0.3, false);
        var empty = new PaddleComparisonAgent.CompareRequest(baseline.city(), baseline.localDate(), baseline.preferredWaterwayType(),
                baseline.asOfUtc(), baseline.departureUtc(), baseline.windowStartUtc(), baseline.windowEndUtc(),
                baseline.predictionTimeUtc(), baseline.syntheticDaylightEndUtc(), List.of());
        var card = run(empty);
        assertNull(card.destination());
        assertEquals("defer", card.status());
    }

    @Test void cityCatalogAndSelectedTimeTravelThroughTheGoalCard() {
        for (String city : List.of("Miami", "Charleston", "Savannah", "NYC")) {
            var expectedCity = SyntheticCityCatalog.normalizeCity(city);
            var card = run(request(city, 91, 30, 1.5, false));
            assertEquals(expectedCity, card.city());
            assertEquals(SyntheticCityCatalog.places(city).get(1).name(), card.destination());
            assertEquals(Instant.parse("2026-10-10T17:00:00Z"), card.windowStartUtc());
            assertEquals(Instant.parse("2026-10-10T19:00:00Z"), card.windowEndUtc());
        }
    }

    @Test void unknownCityAndCrossCityWaterwayAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> SyntheticCityCatalog.places("Atlantis"));
        var request = request("Miami", 10, 30, 0.3, false);
        var alien = new PaddleComparisonAgent.RampInput(
                SyntheticCityCatalog.places("Savannah").getFirst().waterway(), readings("alien", 9.0, 0.6), 10);
        var invalid = new PaddleComparisonAgent.CompareRequest(request.city(), request.localDate(),
                request.preferredWaterwayType(), request.asOfUtc(), request.departureUtc(),
                request.windowStartUtc(), request.windowEndUtc(), request.predictionTimeUtc(),
                request.syntheticDaylightEndUtc(), List.of(alien));
        assertThrows(IllegalArgumentException.class, () -> new PaddleComparisonAgent().gather(invalid));
    }
}
