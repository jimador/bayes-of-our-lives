package dev.jimador.decisiondesk;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcessStatusCode;
import com.embabel.agent.core.ProcessOptions;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DeskRunner implements CommandLineRunner {
    public record Case(PaddleDayAgent.Conditions conditions, String expected) {}
    private static PaddleDayAgent.Conditions c(String id, String issued, String valid,
            Double wind, Double gust, Double swell, Boolean storm, Double height, String phase, Double current) {
        return new PaddleDayAgent.Conditions(id, issued, valid, wind, gust, swell, storm, height, phase, current);
    }
    private static final String FRESH = "2026-10-10T10:00:00Z";
    private static final String VALID = "2026-10-10T18:00:00Z";
    public static final List<Case> CASES = List.of(
            new Case(c("calm", FRESH, VALID, 7.0, 10.0, 1.0, false, 3.0, "rising", 0.4), "candidate"),
            new Case(c("boundary", FRESH, VALID, 12.0, 16.0, 2.0, false, 2.0, "falling", 1.0), "candidate"),
            new Case(c("gust", FRESH, VALID, 8.0, 19.0, 1.0, false, 3.0, "rising", 0.4), "defer"),
            new Case(c("storm", FRESH, VALID, 7.0, 10.0, 1.0, true, 3.0, "falling", 0.4), "defer"),
            new Case(c("lowtide", FRESH, VALID, 7.0, 10.0, 1.0, false, 1.5, "rising", 0.4), "defer"),
            new Case(c("current", FRESH, VALID, 7.0, 10.0, 1.0, false, 3.0, "falling", 1.4), "defer"),
            new Case(c("stale", "2026-10-10T03:00:00Z", VALID, 7.0, 10.0, 1.0, false, 3.0, "rising", 0.4), "review"),
            new Case(c("missingcurrent", FRESH, VALID, 7.0, 10.0, 1.0, false, 3.0, "rising", null), "review"),
            new Case(c("trafficlate", FRESH, VALID, 7.0, 10.0, 1.0, false, 3.0, "rising", 0.4), "defer"),
            new Case(c("trafficunknown", FRESH, VALID, 7.0, 10.0, 1.0, false, 3.0, "rising", 0.4), "review"));

    public static PaddleDayAgent.PlanningRequest request(PaddleDayAgent.Conditions conditions) {
        return new PaddleDayAgent.PlanningRequest(conditions, LocalDate.of(2026, 10, 10), "Horlbeck Creek",
                PaddleDayAgent.AS_OF, PaddleDayAgent.DEPARTURE, PaddleDayAgent.WINDOW_START,
                PaddleDayAgent.WINDOW_END, PaddleDayAgent.PREDICTION_TIME,
                Instant.parse("2026-10-10T22:00:00Z"));
    }

    private final AgentPlatform platform;
    private final String engine;
    private final int warmups;
    private final int runs;
    private final boolean trace;
    private final String mode;
    private final String compareFixture;
    private final String city;
    private final String destination;
    private final String windowStartUtc;
    private final String origin;
    private final Integer simulatedTrafficDelayMinutes;

    public DeskRunner(AgentPlatform platform, @Value("${demo.engine:local}") String engine,
                      @Value("${demo.warmups:1}") int warmups, @Value("${demo.runs:1}") int runs,
                      @Value("${demo.trace:false}") boolean trace,
                      @Value("${demo.mode:baseline}") String mode,
                      @Value("${demo.compare-fixture:calm}") String compareFixture,
                      @Value("${demo.city:charleston}") String city,
                      @Value("${demo.destination:Miami}") String destination,
                      @Value("${demo.window-start-utc:}") String windowStartUtc,
                      @Value("${demo.origin:}") String origin,
                      @Value("${demo.simulated-traffic-delay-minutes:0}") Integer simulatedTrafficDelayMinutes) {
        this.platform = platform;
        this.engine = engine;
        this.warmups = warmups;
        this.runs = runs;
        this.trace = trace;
        this.mode = mode;
        this.compareFixture = compareFixture;
        this.city = city;
        this.destination = destination;
        this.windowStartUtc = windowStartUtc;
        this.origin = origin;
        this.simulatedTrafficDelayMinutes = simulatedTrafficDelayMinutes;
    }

    @Override
    public void run(String... args) {
        if (mode.equals("outing")) {
            runOuting();
            return;
        }
        if (mode.equals("live")) {
            runLive();
            return;
        }
        if (mode.equals("compare")) {
            runComparison();
            return;
        }
        if (!mode.equals("baseline")) throw new IllegalArgumentException("demo.mode must be baseline, compare, outing, or live");
        if (warmups < 0 || runs < 1 || warmups + runs > 10) {
            throw new IllegalArgumentException("Use 0..9 warmups and 1..10 runs, at most 10 rounds total");
        }
        var agent = platform.agents().stream().filter(a -> a.getName().contains("PaddleDayAgent"))
                .findFirst().orElseThrow(() -> new IllegalStateException("PaddleDayAgent was not deployed"));
        System.out.println("engine,phase,round,case_id,expected,raw_choice,card_choice,raw_correct,valid,guarded,decision_ms,process_ms,model,status");
        for (int round = 0; round < warmups + runs; round++) {
            String phase = round < warmups ? "warmup" : "measured";
            for (Case c : CASES) {
                long start = System.nanoTime();
                var process = platform.createAgentProcessFrom(agent, new ProcessOptions(), request(c.conditions())).run();
                double processMs = (System.nanoTime() - start) / 1_000_000.0;
                if (trace) {
                    System.err.println("TRACE " + c.conditions().id() + " " + process.getHistory().stream()
                            .map(com.embabel.agent.core.ActionInvocation::getActionName)
                            .collect(java.util.stream.Collectors.joining(" -> ")));
                }
                var card = process.last(PaddleDayAgent.DayCard.class);
                if (trace && card != null) {
                    System.err.println("CARD " + c.conditions().id() + " destination=" + card.destination()
                            + " raw=" + card.rawChoice() + " card=" + card.choice() + " status=" + card.status());
                }
                String raw = card == null ? "none" : card.rawChoice();
                String cardChoice = card == null ? "none" : card.choice();
                String status = card == null ? String.valueOf(process.getStatus()) : card.status();
                boolean valid = process.getStatus() == AgentProcessStatusCode.COMPLETED && card != null
                        && card.nextSteps().length > 0 && (status.equals("selected") || status.equals("guarded"));
                double decisionMs = card == null ? 0 : card.decisionNanos() / 1_000_000.0;
                String model = card == null ? "none" : card.model();
                System.out.printf(java.util.Locale.ROOT, "%s,%s,%d,%s,%s,%s,%s,%s,%s,%s,%.3f,%.3f,%s,%s%n",
                        engine, phase, round, c.conditions().id(), c.expected(), raw, cardChoice, c.expected().equals(raw),
                        valid, status.equals("guarded"), decisionMs, processMs, model, status);
            }
        }
    }

    private void runOuting() {
        Instant now = Instant.now();
        Instant start = windowStartUtc.isBlank() ? null : Instant.parse(windowStartUtc);
        if (start != null && (start.isBefore(now) || start.isAfter(now.plusSeconds(6 * 86400L))))
            throw new IllegalArgumentException("Outing window must start within the next six days");
        var agent = platform.agents().stream().filter(a -> a.getName().contains("CityAfternoonAgent"))
                .findFirst().orElseThrow(() -> new IllegalStateException("CityAfternoonAgent was not deployed"));
        var request = new CityAfternoonAgent.Request(destination, origin, start, simulatedTrafficDelayMinutes);
        var process = platform.createAgentProcessFrom(agent, new ProcessOptions(), request).run();
        if (process.getStatus() != AgentProcessStatusCode.COMPLETED)
            throw new IllegalStateException("Outing process: " + process.getStatus() + " " + process.getFailureInfo());
        var plan = process.last(CityAfternoonAgent.Plan.class);
        System.out.println("engine=" + engine + " mode=outing city=" + plan.city() + " status=" + plan.status()
                + " selectedResearchLead=" + plan.selectedResearchLead() + " rankedOptions=" + plan.rankedOptions()
                + " forecastStartUtc=" + plan.startUtc() + " retrievedUtc=" + plan.retrievedUtc()
                + " geocodingSource=" + plan.geocodingUrl()
                + " preferenceSource=" + plan.preferences().value().source());
        System.out.println("placeData=© OpenStreetMap contributors (ODbL) https://www.openstreetmap.org/copyright");
        for (var a : plan.assessments()) {
            var o = a.option();
            System.out.println("place=" + o.place().name() + " source=" + o.place().url()
                    + " sampleUtc=" + o.sampleUtc() + " weatherSource=" + o.weatherUrl()
                    + " temperatureC=" + o.temperatureC() + " precipitationProbabilityPercent=" + o.precipitationProbability()
                    + " windKt=" + o.windKt() + " osrmBaseDriveMinutes=" + o.baseDriveMinutes()
                    + " simulatedTrafficDelayMinutes=" + simulatedTrafficDelayMinutes
                    + " localPolicy=" + a.localPolicy() + " proposition=" + a.proposition()
                    + " choice=" + a.choice() + " rating=" + a.rating()
                    + " classification=" + a.classification() + " model=" + a.model()
                    + " decisionMs=" + a.decisionNanos() / 1_000_000.0
                    + " classificationMs=" + a.classificationNanos() / 1_000_000.0);
        }
        System.out.println("hoursAdmissionAccessibilityUnverified=true liveTrafficUnknown=true fictionalThresholds=true");
        System.out.println("actions=" + process.getHistory().stream()
                .map(com.embabel.agent.core.ActionInvocation::getActionName)
                .collect(java.util.stream.Collectors.joining(" -> ")));
    }

    private void runLive() {
        if (simulatedTrafficDelayMinutes == null || simulatedTrafficDelayMinutes < 0
                || simulatedTrafficDelayMinutes > 180)
            throw new IllegalArgumentException("Simulated traffic delay must be 0..180 minutes");
        Instant now = Instant.now();
        Instant start = windowStartUtc.isBlank() ? now.plusSeconds(7200).truncatedTo(java.time.temporal.ChronoUnit.HOURS)
                : Instant.parse(windowStartUtc);
        if (start.isBefore(now) || start.isAfter(now.plusSeconds(6 * 86400L)))
            throw new IllegalArgumentException("Live window must start within the next six days");
        var agent = platform.agents().stream().filter(a -> a.getName().contains("PaddleComparisonAgent"))
                .findFirst().orElseThrow();
        var request = new PaddleComparisonAgent.CompareRequest(destination,
                LocalDate.ofInstant(start, java.time.ZoneOffset.UTC), "intracoastal", now,
                start.minusSeconds(7200), start, start.plusSeconds(7200), start.plusSeconds(3600),
                null, List.of(), true, origin, simulatedTrafficDelayMinutes);
        var process = platform.createAgentProcessFrom(agent, new ProcessOptions(), request).run();
        if (process.getStatus() != AgentProcessStatusCode.COMPLETED)
            throw new IllegalStateException("Live planning process: " + process.getStatus() + " " + process.getFailureInfo());
        var card = process.last(PaddleComparisonAgent.ComparisonCard.class);
        System.out.println("destination=" + destination + " status=" + card.status() + " researchPlace=" + card.destination()
                + " windowUtc=" + card.windowStartUtc() + "/" + card.windowEndUtc()
                + " rankedOptions=" + card.rankedOptions()
                + " retrievedForecastsAreModels=true accessUnverified=true liveTrafficUnknown=true"
                + " simulatedTrafficDelayMinutes=" + simulatedTrafficDelayMinutes);
        System.out.println("placeData=© OpenStreetMap contributors (ODbL) https://www.openstreetmap.org/copyright");
        for (var a : card.assessments()) {
            var c = a.input().conditions();
            System.out.println("place=" + a.place().name() + " source=" + c.id() + " windowUtc=" + a.windowStartUtc()
                    + "/" + a.windowEndUtc() + " windKt=" + c.windKt() + " gustKt=" + c.gustKt()
                    + " waveFt=" + c.swellFt() + " oceanCurrentKt=" + c.tidalCurrentKt()
                    + " osrmBaseDriveMinutes=" + a.input().baseDriveMinutes()
                    + " simulatedTrafficDelayMinutes=" + a.input().trafficDelayMinutes()
                    + " localPolicy=" + a.localPolicy() + " proposition=" + a.proposition()
                    + " choice=" + a.choice() + " rating=" + a.ratingProjection()
                    + " model=" + a.choiceModel());
            var noaa = a.noaaContext();
            if (noaa != null) {
                var tide = noaa.nearestTide();
                var current = noaa.nearestCurrent();
                System.out.println("noaaTideStation=" + (tide == null ? "none" : tide.id() + "/" + tide.name())
                        + " distanceKm=" + (tide == null ? "unknown" : tide.distanceKm())
                        + " status=" + noaa.tideStatus() + " predictionSource=" + noaa.tidePredictionUrl()
                        + " highLowFtMllw=" + noaa.tideEvents());
                System.out.println("noaaCurrentStation=" + (current == null ? "none" : current.id() + "/" + current.name())
                        + " distanceKm=" + (current == null ? "unknown" : current.distanceKm())
                        + " status=" + noaa.currentStatus());
            }
        }
        System.out.println("actions=" + process.getHistory().stream()
                .map(com.embabel.agent.core.ActionInvocation::getActionName)
                .collect(java.util.stream.Collectors.joining(" -> ")));
    }

    private void runComparison() {
        if (!compareFixture.equals("calm") && !compareFixture.equals("mixed"))
            throw new IllegalArgumentException("demo.compare-fixture must be calm or mixed");
        boolean mixed = compareFixture.equals("mixed");
        String normalizedCity = SyntheticCityCatalog.normalizeCity(city);
        var places = SyntheticCityCatalog.places(normalizedCity);
        var agent = platform.agents().stream().filter(a -> a.getName().contains("PaddleComparisonAgent"))
                .findFirst().orElseThrow(() -> new IllegalStateException("PaddleComparisonAgent was not deployed"));
        var request = new PaddleComparisonAgent.CompareRequest(normalizedCity, LocalDate.of(2026, 10, 10), "intracoastal",
                Instant.parse("2026-10-10T14:00:00Z"), Instant.parse("2026-10-10T14:00:00Z"),
                Instant.parse("2026-10-10T16:00:00Z"), Instant.parse("2026-10-10T18:00:00Z"),
                Instant.parse("2026-10-10T17:00:00Z"), Instant.parse("2026-10-10T21:00:00Z"),
                List.of(new PaddleComparisonAgent.RampInput(places.get(0).waterway(),
                                c("demo-a", "2026-10-10T12:00:00Z", "2026-10-10T20:00:00Z",
                                        9.0, 10.0, 1.0, false, 3.0, "rising", 0.6), 10),
                        new PaddleComparisonAgent.RampInput(places.get(1).waterway(),
                                c("demo-b", "2026-10-10T12:00:00Z", "2026-10-10T20:00:00Z",
                                        7.0, 10.0, 1.0, false, 3.0, "rising", 0.4), mixed ? 181 : 30),
                        new PaddleComparisonAgent.RampInput(places.get(2).waterway(),
                                c("demo-c", "2026-10-10T12:00:00Z", "2026-10-10T20:00:00Z",
                                        8.0, 10.0, 1.0, false, 3.0, "rising", mixed ? null : 0.3), 20)));
        long processStart = System.nanoTime();
        var process = platform.createAgentProcessFrom(agent, new ProcessOptions(), request).run();
        double processMs = (System.nanoTime() - processStart) / 1_000_000.0;
        if (process.getStatus() != AgentProcessStatusCode.COMPLETED)
            throw new IllegalStateException("Comparison process status: " + process.getStatus());
        var card = process.last(PaddleComparisonAgent.ComparisonCard.class);
        System.out.println("engine=" + engine + " mode=compare city=" + card.city() + " fixture=" + compareFixture + " status=" + card.status()
                + " selected=" + card.destination() + " windowUtc=" + card.windowStartUtc() + "/" + card.windowEndUtc()
                + " rankedOptions=" + card.rankedOptions()
                + " preferenceSource=" + card.profile().source() + " preferredType=" + card.profile().preferredWaterwayType());
        System.out.println("engine,city,fixture,place,window_start_utc,window_end_utc,local_policy,expected_proposition,raw_proposition,proposition_correct,expected_choice,raw_choice,choice_correct,expected_rating,raw_rating,rating_projection,rating_evidence,rating_correct,expected_classification,raw_classification,classification_correct,eligible,decision_ms,classification_ms,rank_ms,process_ms,proposition_model,choice_model,rating_model,classification_model,card_status,selected_place");
        for (var a : card.assessments()) {
            String expectedProposition = Boolean.toString(!a.localPolicy().equals("review"));
            String expectedRating = switch (a.localPolicy()) { case "candidate" -> "easy"; case "defer" -> "hard"; default -> "unknown"; };
            String expectedClassification = a.place().routeSetting();
            boolean eligible = card.rankedOptions().contains(a.place().name());
            System.out.printf(java.util.Locale.ROOT,
                    "%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%.3f,%.3f,%.3f,%.3f,%s,%s,%s,%s,%s,%s%n",
                    engine, card.city(), compareFixture, a.place().name(), a.windowStartUtc(), a.windowEndUtc(), a.localPolicy(),
                    expectedProposition, a.proposition(), expectedProposition.equals(a.proposition()),
                    a.localPolicy(), a.choice(), a.localPolicy().equals(a.choice()),
                    expectedRating, a.rating(), a.ratingProjection(), a.ratingEvidence(), expectedRating.equals(a.ratingProjection()),
                    expectedClassification, a.classification(), expectedClassification.equals(a.classification()),
                    eligible, a.decisionNanos() / 1_000_000.0, a.classificationNanos() / 1_000_000.0,
                    card.rankNanos() / 1_000_000.0, processMs, a.propositionModel(), a.choiceModel(),
                    a.ratingModel(), a.classificationModel(), card.status(), card.destination());
        }
        System.out.println("actions=" + process.getHistory().stream()
                .map(com.embabel.agent.core.ActionInvocation::getActionName)
                .collect(java.util.stream.Collectors.joining(" -> ")));
    }
}
