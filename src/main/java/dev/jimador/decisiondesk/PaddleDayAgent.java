package dev.jimador.decisiondesk;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.Ai;
import com.embabel.common.ai.classification.ClassificationResult;
import com.embabel.common.ai.decision.DecisionAnswer;
import com.embabel.common.ai.decision.DecisionSpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@Agent(description = "Sort a synthetic coastal kayaking window into candidate, review, or defer")
public class PaddleDayAgent {
    public static final Instant AS_OF = Instant.parse("2026-10-10T12:00:00Z");
    public static final Instant DEPARTURE = Instant.parse("2026-10-10T14:00:00Z");
    public static final Instant WINDOW_START = Instant.parse("2026-10-10T15:00:00Z");
    public static final Instant WINDOW_END = Instant.parse("2026-10-10T17:00:00Z");
    public static final Instant PREDICTION_TIME = Instant.parse("2026-10-10T16:00:00Z");

    public record Conditions(String id, String forecastIssuedUtc, String forecastValidUntilUtc,
                             Double windKt, Double gustKt, Double swellFt, Boolean stormSignal,
                             Double waterHeightFtMllw, String tidePhase, Double tidalCurrentKt) {}
    public record Brief(String id, String text) {}
    public record Decision(String id, String rawChoice, String choice, String status, long decisionNanos, String model) {}
    public record DayCard(String id, String rawChoice, String choice, String status, long decisionNanos, String model, String[] nextSteps) {}

    private final SimulatedTrafficProvider traffic = new SimulatedTrafficProvider();

    private static final DecisionSpec WINDOW = DecisionSpec.builder()
            .choice("window", q -> q.asking("Apply this illustrative sorting policy to the synthetic coastal record. " +
                            "Choose review first if any required value is unknown, forecast age is over 6 hours or negative, or forecast does not cover the whole window. " +
                            "Otherwise choose defer if storm signal is true, sustained wind is over 12 kt, gust is over 16 kt, swell is over 2 ft, " +
                            "predicted water height is below 2 ft MLLW, tidal current is over 1 kt, or simulated traffic arrival is after the window starts. " +
                            "Otherwise choose candidate. Exactly at a numeric boundary is candidate. Tide phase is context, not a proxy for current. " +
                            "These fictional thresholds are not real launch advice.")
                    .option("candidate", "All required data present and fresh, window covered, and all illustrative numeric conditions within the demo band; needs human review before any outing")
                    .option("review", "Missing, stale, future-dated, or incomplete forecast or tide/current data; obtain authoritative local information")
                    .option("defer", "Complete data but a storm signal, illustrative limit exceeded, or simulated traffic misses the window start; do not propose this window"))
            .build();

    @Action
    public Brief prepare(Conditions conditions) {
        Double ageHours = null;
        Boolean coversWindow = null;
        try {
            if (conditions.forecastIssuedUtc() != null) {
                ageHours = Duration.between(Instant.parse(conditions.forecastIssuedUtc()), AS_OF).toMinutes() / 60.0;
            }
            if (conditions.forecastValidUntilUtc() != null) {
                coversWindow = !Instant.parse(conditions.forecastValidUntilUtc()).isBefore(WINDOW_END);
            }
        } catch (RuntimeException invalidTimestamp) {
            // Bad timestamps stay unknown and force review in the decision contract.
        }
        var drive = traffic.estimate(conditions.id());
        Instant arrival = drive.totalMinutes() == null ? null : DEPARTURE.plusSeconds(drive.totalMinutes() * 60L);
        String text = String.format(Locale.ROOT, """
                Synthetic coastal kayak planning record, not a live forecast.
                As of UTC: %s
                Proposed window UTC: %s to %s
                Simulated road departure UTC: %s
                Simulated base drive minutes: %s
                Simulated traffic delay minutes: %s
                Simulated arrival UTC: %s
                Forecast issued UTC: %s
                Forecast valid through UTC: %s
                Forecast age hours: %s
                Forecast covers whole window: %s
                Sustained wind kt: %s
                Wind gust kt: %s
                Swell ft: %s
                Storm signal: %s
                Predicted tide time UTC: %s
                Predicted water height ft above MLLW: %s
                Tide phase: %s
                Predicted current time UTC: %s
                Tidal current kt at separate synthetic current station: %s
                """, AS_OF, WINDOW_START, WINDOW_END, DEPARTURE,
                value(drive.baseMinutes()), value(drive.delayMinutes()), value(arrival),
                value(conditions.forecastIssuedUtc()), value(conditions.forecastValidUntilUtc()),
                value(ageHours), value(coversWindow), value(conditions.windKt()), value(conditions.gustKt()),
                value(conditions.swellFt()), value(conditions.stormSignal()), PREDICTION_TIME,
                value(conditions.waterHeightFtMllw()), value(conditions.tidePhase()), PREDICTION_TIME,
                value(conditions.tidalCurrentKt()));
        return new Brief(conditions.id(), text);
    }

    private static String value(Object value) { return value == null ? "unknown" : value.toString(); }

    @Action
    public Decision choose(Brief brief, Ai ai) {
        long start = System.nanoTime();
        var response = ai.decisions().named("activeDecisionService").ask(brief.text(), WINDOW);
        long nanos = System.nanoTime() - start;
        var result = ((DecisionAnswer.Choice) response.answer("window")).getOutcome();
        if (result instanceof ClassificationResult.Selected selected) {
            String raw = selected.getCategoryId();
            String guarded = guardCandidate(raw, brief.text());
            return new Decision(brief.id(), raw, guarded, raw.equals(guarded) ? "selected" : "guarded", nanos,
                    selected.getProvenance().getModelName());
        }
        return new Decision(brief.id(), "none", "review", result.getClass().getSimpleName(), nanos, "none");
    }

    static String guardCandidate(String raw, String brief) {
        return raw.equals("candidate") && !CoastalPolicy.evaluate(brief).equals("candidate") ? "review" : raw;
    }

    @Action
    @AchievesGoal(description = "Produce a coastal kayaking planning card for human review")
    public DayCard finish(Decision decision) {
        String[] steps = switch (decision.choice()) {
            case "candidate" -> new String[] {"Treat this as a planning candidate only", "Check current official marine forecast, tide and current predictions for the actual location", "Make the final local go/no-go decision yourself"};
            case "defer" -> new String[] {"Do not propose this synthetic window", "Look at another day using current local information"};
            default -> new String[] {"Obtain missing or current official information", "Review the local launch, tide, current, and weather context yourself"};
        };
        return new DayCard(decision.id(), decision.rawChoice(), decision.choice(), decision.status(), decision.decisionNanos(), decision.model(), steps);
    }
}
