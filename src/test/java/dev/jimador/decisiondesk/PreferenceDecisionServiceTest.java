package dev.jimador.decisiondesk;

import static org.junit.jupiter.api.Assertions.*;

import com.embabel.common.ai.classification.ClassificationRequest;
import com.embabel.common.ai.classification.ClassificationResult;
import com.embabel.common.ai.classification.ClassificationSpec;
import org.junit.jupiter.api.Test;

class PreferenceDecisionServiceTest {
    private final PreferenceDecisionService service = new PreferenceDecisionService();
    private final PaddleDayAgent agent = new PaddleDayAgent();
    private final ClassificationSpec spec = ClassificationSpec.builder()
            .asking("Apply the illustrative coastal window policy")
            .category("candidate", "Candidate for human review")
            .category("review", "Missing or stale data")
            .category("defer", "Illustrative limit exceeded")
            .build();

    @Test void candidateGuardCatchesLowWaterAndLateTraffic() {
        for (var c : DeskRunner.CASES) {
            String brief = agent.prepare(c.conditions()).text();
            String guarded = PaddleDayAgent.guardCandidate("candidate", brief);
            assertEquals(c.expected().equals("candidate") ? "candidate" : "review", guarded, c.conditions().id());
        }
    }

    @Test void uncoveredForecastGoesToReview() {
        var calm = DeskRunner.CASES.getFirst().conditions();
        var uncovered = new PaddleDayAgent.Conditions(calm.id(), calm.forecastIssuedUtc(),
                "2026-10-10T16:00:00Z", calm.windKt(), calm.gustKt(), calm.swellFt(),
                calm.stormSignal(), calm.waterHeightFtMllw(), calm.tidePhase(), calm.tidalCurrentKt());
        assertEquals("review", CoastalPolicy.evaluate(agent.prepare(uncovered).text()));
    }

    @Test void belowDatumHeightStillCountsAsLowWater() {
        var calm = DeskRunner.CASES.getFirst().conditions();
        var belowDatum = new PaddleDayAgent.Conditions(calm.id(), calm.forecastIssuedUtc(),
                calm.forecastValidUntilUtc(), calm.windKt(), calm.gustKt(), calm.swellFt(),
                calm.stormSignal(), -0.5, calm.tidePhase(), calm.tidalCurrentKt());
        assertEquals("defer", CoastalPolicy.evaluate(agent.prepare(belowDatum).text()));
    }

    @Test void sortsWeatherTideCurrentAndTrafficBoundaries() {
        for (var c : DeskRunner.CASES) {
            String text = agent.prepare(c.conditions()).text();
            var result = service.classify(ClassificationRequest.of(text, spec));
            assertInstanceOf(ClassificationResult.Selected.class, result);
            assertEquals(c.expected(), ((ClassificationResult.Selected) result).getCategoryId(), c.conditions().id());
        }
    }
}
