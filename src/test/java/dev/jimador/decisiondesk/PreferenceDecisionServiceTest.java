package dev.jimador.decisiondesk;

import static org.junit.jupiter.api.Assertions.*;

import com.embabel.common.ai.classification.ClassificationRequest;
import com.embabel.common.ai.classification.ClassificationResult;
import com.embabel.common.ai.classification.ClassificationSpec;
import com.embabel.common.ai.decision.PropositionRequest;
import com.embabel.common.ai.decision.PropositionResult;
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

    private String brief(PaddleDayAgent.Conditions conditions) {
        var request = DeskRunner.request(conditions);
        var destination = agent.destination(request, agent.destinations(request));
        return agent.prepare(request, destination, agent.season(request), agent.weather(request),
                agent.tides(request), agent.traffic(request, destination)).text();
    }

    @Test void candidateGuardCatchesLowWaterAndLateTraffic() {
        for (var c : DeskRunner.CASES) {
            String brief = brief(c.conditions());
            String guarded = PaddleDayAgent.guardCandidate("candidate", brief);
            assertEquals(c.expected().equals("candidate") ? "candidate" : "review", guarded, c.conditions().id());
        }
    }

    @Test void uncoveredForecastGoesToReview() {
        var calm = DeskRunner.CASES.getFirst().conditions();
        var uncovered = new PaddleDayAgent.Conditions(calm.id(), calm.forecastIssuedUtc(),
                "2026-10-10T16:00:00Z", calm.windKt(), calm.gustKt(), calm.swellFt(),
                calm.stormSignal(), calm.waterHeightFtMllw(), calm.tidePhase(), calm.tidalCurrentKt());
        assertEquals("review", CoastalPolicy.evaluate(brief(uncovered)));
    }

    @Test void belowDatumHeightStillCountsAsLowWater() {
        var calm = DeskRunner.CASES.getFirst().conditions();
        var belowDatum = new PaddleDayAgent.Conditions(calm.id(), calm.forecastIssuedUtc(),
                calm.forecastValidUntilUtc(), calm.windKt(), calm.gustKt(), calm.swellFt(),
                calm.stormSignal(), -0.5, calm.tidePhase(), calm.tidalCurrentKt());
        assertEquals("defer", CoastalPolicy.evaluate(brief(belowDatum)));
    }

    @Test void syntheticDaylightCutoffCanDeferTheWindow() {
        var request = DeskRunner.request(DeskRunner.CASES.getFirst().conditions());
        var earlyDark = new PaddleDayAgent.PlanningRequest(request.conditions(), request.localDate(),
                request.waterwayInterest(), request.asOfUtc(), request.departureUtc(), request.windowStartUtc(),
                request.windowEndUtc(), request.predictionTimeUtc(), java.time.Instant.parse("2026-10-10T16:00:00Z"));
        var destination = agent.destination(earlyDark, agent.destinations(earlyDark));
        String brief = agent.prepare(earlyDark, destination, agent.season(earlyDark), agent.weather(earlyDark),
                agent.tides(earlyDark), agent.traffic(earlyDark, destination)).text();
        assertEquals("defer", CoastalPolicy.evaluate(brief));
    }

    @Test void sortsWeatherTideCurrentAndTrafficBoundaries() {
        for (var c : DeskRunner.CASES) {
            String text = brief(c.conditions());
            var result = service.classify(ClassificationRequest.of(text, spec));
            assertInstanceOf(ClassificationResult.Selected.class, result);
            assertEquals(c.expected(), ((ClassificationResult.Selected) result).getCategoryId(), c.conditions().id());
        }
    }

    @Test void directPropositionRequestKeepsFalseDistinctFromFailure() {
        var trueAnswer = service.assess(new PropositionRequest(brief(DeskRunner.CASES.getFirst().conditions()),
                "Are the synthetic readings complete and current enough?"));
        assertInstanceOf(PropositionResult.Answered.class, trueAnswer);
        assertTrue(((PropositionResult.Answered) trueAnswer).getAnswer());
        var falseAnswer = service.assess(new PropositionRequest(brief(DeskRunner.CASES.get(7).conditions()),
                "Are the synthetic readings complete and current enough?"));
        assertInstanceOf(PropositionResult.Answered.class, falseAnswer);
        assertFalse(((PropositionResult.Answered) falseAnswer).getAnswer());
    }
}
