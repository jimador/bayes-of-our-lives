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
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Agent(description = "Plan a synthetic Charleston kayaking window for human review")
public class PaddleDayAgent {
    public static final Instant AS_OF = Instant.parse("2026-10-10T12:00:00Z");
    public static final Instant DEPARTURE = Instant.parse("2026-10-10T14:00:00Z");
    public static final Instant WINDOW_START = Instant.parse("2026-10-10T15:00:00Z");
    public static final Instant WINDOW_END = Instant.parse("2026-10-10T17:00:00Z");
    public static final Instant PREDICTION_TIME = Instant.parse("2026-10-10T16:00:00Z");

    public record Conditions(String id, String forecastIssuedUtc, String forecastValidUntilUtc,
                             Double windKt, Double gustKt, Double swellFt, Boolean stormSignal,
                             Double waterHeightFtMllw, String tidePhase, Double tidalCurrentKt) {}
    public record PlanningRequest(Conditions conditions, LocalDate localDate, String waterwayInterest,
                                  Instant asOfUtc, Instant departureUtc, Instant windowStartUtc,
                                  Instant windowEndUtc, Instant predictionTimeUtc, Instant syntheticDaylightEndUtc) {}
    public record Destination(String name, String waterway) {}
    public record Destinations(List<Destination> choices) {}
    public record SeasonContext(String season, LocalDate localDate, Instant daylightEndUtc) {}
    public record Weather(String issuedUtc, String validUntilUtc, Double windKt, Double gustKt,
                          Double swellFt, Boolean stormSignal) {}
    public record Tides(Double heightFtMllw, String phase, Double currentKt) {}
    public record Drive(Integer baseMinutes, Integer delayMinutes, Instant arrivalUtc) {}
    public record Brief(String id, String text) {}
    public record Decision(String id, String rawChoice, String choice, String status, long decisionNanos, String model) {}
    public record DayCard(String id, String destination, String rawChoice, String choice, String status,
                          long decisionNanos, String model, String[] nextSteps) {}

    private final SimulatedTrafficProvider trafficProvider = new SimulatedTrafficProvider();

    private static final DecisionSpec WINDOW = DecisionSpec.builder()
            .choice("window", q -> q.asking("Apply this illustrative sorting policy to the synthetic Charleston record. " +
                            "Choose review first if any required value is unknown, forecast age is over 6 hours or negative, or forecast does not cover the whole window. " +
                            "Otherwise choose defer if storm signal is true, sustained wind is over 12 kt, gust is over 16 kt, swell is over 2 ft, " +
                            "predicted water height is below 2 ft MLLW, tidal current is over 1 kt, simulated traffic arrival is after the window starts, " +
                            "or the window ends after the supplied synthetic daylight cutoff. " +
                            "Otherwise choose candidate. Exactly at a numeric or time boundary is candidate. Tide phase is context, not a proxy for current. " +
                            "These fictional thresholds are not real launch advice.")
                    .option("candidate", "All required data present and fresh, window covered, and all illustrative conditions within the demo band; needs human review before any outing")
                    .option("review", "Missing, stale, future-dated, or incomplete forecast, tide/current, road, or daylight data; obtain authoritative local information")
                    .option("defer", "Complete data but a storm signal, illustrative limit exceeded, simulated traffic misses the window start, or window passes daylight cutoff"))
            .build();

    @Action
    public Destinations destinations(PlanningRequest request) {
        return new Destinations(List.of(
                new Destination("Palmetto Islands County Park", "Horlbeck Creek"),
                new Destination("Pierpoint Boat Landing", "Church Creek"),
                new Destination("Gadsdenville Boat Landing", "Copahee Sound")));
    }

    @Action
    public Destination destination(PlanningRequest request, Destinations options) {
        return options.choices().stream()
                .filter(place -> place.waterway().equalsIgnoreCase(request.waterwayInterest()))
                .findFirst().orElse(options.choices().getFirst());
    }

    @Action
    public SeasonContext season(PlanningRequest request) {
        int month = request.localDate().getMonthValue();
        String name = month >= 3 && month <= 5 ? "spring" : month >= 6 && month <= 8 ? "summer"
                : month >= 9 && month <= 11 ? "autumn" : "winter";
        return new SeasonContext(name, request.localDate(), request.syntheticDaylightEndUtc());
    }

    @Action
    public Weather weather(PlanningRequest request) {
        var c = request.conditions();
        return new Weather(c.forecastIssuedUtc(), c.forecastValidUntilUtc(), c.windKt(), c.gustKt(),
                c.swellFt(), c.stormSignal());
    }

    @Action
    public Tides tides(PlanningRequest request) {
        var c = request.conditions();
        return new Tides(c.waterHeightFtMllw(), c.tidePhase(), c.tidalCurrentKt());
    }

    @Action
    public Drive traffic(PlanningRequest request, Destination destination) {
        var estimate = trafficProvider.estimate(request.conditions().id());
        Instant arrival = estimate.totalMinutes() == null ? null : request.departureUtc().plusSeconds(estimate.totalMinutes() * 60L);
        return new Drive(estimate.baseMinutes(), estimate.delayMinutes(), arrival);
    }

    @Action
    public Brief prepare(PlanningRequest request, Destination destination, SeasonContext season,
                         Weather weather, Tides tides, Drive drive) {
        Double ageHours = null;
        Boolean coversWindow = null;
        try {
            if (weather.issuedUtc() != null) {
                ageHours = Duration.between(Instant.parse(weather.issuedUtc()), request.asOfUtc()).toMinutes() / 60.0;
            }
            if (weather.validUntilUtc() != null) {
                coversWindow = !Instant.parse(weather.validUntilUtc()).isBefore(request.windowEndUtc());
            }
        } catch (RuntimeException invalidTimestamp) {
            // Invalid timestamps remain unknown for review.
        }
        String text = String.format(Locale.ROOT, """
                Synthetic coastal kayak planning record, not a live forecast.
                Planning date supplied by request: %s
                Season: %s
                Destination: %s
                Waterway: %s
                Destination access: unverified; check current park or landing status
                As of UTC: %s
                Proposed window UTC: %s to %s
                Synthetic daylight cutoff UTC: %s
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
                """, season.localDate(), season.season(), destination.name(), destination.waterway(),
                request.asOfUtc(), request.windowStartUtc(), request.windowEndUtc(),
                value(season.daylightEndUtc()), request.departureUtc(),
                value(drive.baseMinutes()), value(drive.delayMinutes()), value(drive.arrivalUtc()),
                value(weather.issuedUtc()), value(weather.validUntilUtc()), value(ageHours), value(coversWindow),
                value(weather.windKt()), value(weather.gustKt()), value(weather.swellFt()), value(weather.stormSignal()),
                request.predictionTimeUtc(), value(tides.heightFtMllw()), value(tides.phase()),
                request.predictionTimeUtc(),
                value(tides.currentKt()));
        return new Brief(request.conditions().id(), text);
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
    @AchievesGoal(description = "Produce a Charleston coastal kayaking planning card for human review")
    public DayCard finish(Decision decision, Destination destination) {
        String[] steps = switch (decision.choice()) {
            case "candidate" -> new String[] {"Treat this as a planning candidate only", "Check current official marine forecast, tide and current predictions for the actual waterway", "Confirm landing access and make the final local decision yourself"};
            case "defer" -> new String[] {"Do not propose this synthetic window", "Look at another date or destination using current local information"};
            default -> new String[] {"Obtain missing or current official information", "Review destination access, tide, current, weather, and travel timing yourself"};
        };
        return new DayCard(decision.id(), destination.name(), decision.rawChoice(), decision.choice(), decision.status(),
                decision.decisionNanos(), decision.model(), steps);
    }
}
