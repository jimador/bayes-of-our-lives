package dev.jimador.decisiondesk;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.Ai;
import com.embabel.agent.api.common.ranking.Ranker;
import com.embabel.agent.api.common.ranking.Ranking;
import com.embabel.agent.api.common.ranking.Rankings;
import com.embabel.common.ai.classification.ClassificationRequest;
import com.embabel.common.ai.classification.ClassificationResult;
import com.embabel.common.ai.classification.ClassificationSpec;
import com.embabel.common.ai.classification.ModelProvenance;
import com.embabel.common.ai.decision.DecisionAnswer;
import com.embabel.common.ai.decision.DecisionRequest;
import com.embabel.common.ai.decision.DecisionSpec;
import com.embabel.common.ai.decision.PropositionResult;
import com.embabel.common.ai.decision.RatingResult;
import com.embabel.common.core.types.Described;
import com.embabel.common.core.types.Named;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

/** The same decision questions assess fixtures or sourced research options. */
@Agent(description = "Compare coastal kayak research options for a requested place and window")
public class PaddleComparisonAgent {
    public record RampInput(String waterway, PaddleDayAgent.Conditions conditions,
                            Integer trafficDelayMinutes, Integer baseDriveMinutes) {
        public RampInput(String waterway, PaddleDayAgent.Conditions conditions, Integer trafficDelayMinutes) {
            this(waterway, conditions, trafficDelayMinutes, 30);
        }
    }
    public record CompareRequest(String city, LocalDate localDate, String preferredWaterwayType, Instant asOfUtc,
                                 Instant departureUtc, Instant windowStartUtc, Instant windowEndUtc,
                                 Instant predictionTimeUtc, Instant syntheticDaylightEndUtc,
                                 List<RampInput> ramps, boolean live, String origin, Integer simulatedTrafficDelayMinutes) {
        public CompareRequest(String city, LocalDate localDate, String preferredWaterwayType, Instant asOfUtc,
                              Instant departureUtc, Instant windowStartUtc, Instant windowEndUtc,
                              Instant predictionTimeUtc, Instant syntheticDaylightEndUtc, List<RampInput> ramps) {
            this(city, localDate, preferredWaterwayType, asOfUtc, departureUtc, windowStartUtc, windowEndUtc,
                    predictionTimeUtc, syntheticDaylightEndUtc, ramps, false, "", null);
        }
        public CompareRequest(String city, LocalDate localDate, String preferredWaterwayType, Instant asOfUtc,
                              Instant departureUtc, Instant windowStartUtc, Instant windowEndUtc,
                              Instant predictionTimeUtc, Instant syntheticDaylightEndUtc,
                              List<RampInput> ramps, boolean live) {
            this(city, localDate, preferredWaterwayType, asOfUtc, departureUtc, windowStartUtc, windowEndUtc,
                    predictionTimeUtc, syntheticDaylightEndUtc, ramps, live, "", null);
        }
        public CompareRequest(String city, LocalDate localDate, String preferredWaterwayType, Instant asOfUtc,
                              Instant departureUtc, Instant windowStartUtc, Instant windowEndUtc,
                              Instant predictionTimeUtc, Instant syntheticDaylightEndUtc,
                              List<RampInput> ramps, boolean live, String origin) {
            this(city, localDate, preferredWaterwayType, asOfUtc, departureUtc, windowStartUtc, windowEndUtc,
                    predictionTimeUtc, syntheticDaylightEndUtc, ramps, live, origin, null);
        }
    }
    public record RampBrief(SyntheticCityCatalog.Place place, RampInput input, Instant windowStartUtc,
                            Instant windowEndUtc, String text, boolean live, NoaaCoopsProvider.Context noaaContext) {}
    public record RampBriefs(List<RampBrief> values) {}
    public record RampAssessment(SyntheticCityCatalog.Place place, RampInput input, Instant windowStartUtc,
                                 Instant windowEndUtc, String localPolicy, boolean live,
                                 NoaaCoopsProvider.Context noaaContext,
                                 String proposition, String choice, String rating, String ratingProjection,
                                 String ratingEvidence, String classification,
                                 String propositionModel, String choiceModel, String ratingModel, String classificationModel,
                                 long decisionNanos, long classificationNanos) {}
    public record Assessments(List<RampAssessment> values) {}
    public record RampCandidate(RampAssessment assessment) implements Named, Described {
        @Override public String getName() {
            return assessment.live() ? assessment.place().name() + " @ " + assessment.windowStartUtc()
                    : assessment.place().name();
        }
        @Override public String getDescription() { return assessment.place().waterway(); }
    }
    public record PreferenceProfile(String source, String preferredWaterwayType, double preferredTypeBonus,
                                    double shortDriveWeight, double lowWindWeight) {}
    public record RankedRamps(List<String> names, String selected, Instant selectedStartUtc,
                              Instant selectedEndUtc, String fallback, boolean provisional, PreferenceProfile profile,
                              long rankNanos) {}
    public record ComparisonCard(String city, String destination, Instant windowStartUtc, Instant windowEndUtc,
                                 String status, List<String> rankedOptions,
                                 List<RampAssessment> assessments, PreferenceProfile profile, long rankNanos) {}

    @Autowired private DicePersonaService personaService;
    @Autowired private TripDataProvider liveProvider;

    private static final DecisionSpec QUESTIONS = DecisionSpec.builder()
            .proposition("complete", q -> q.asking("Are all required synthetic readings present and the forecast current and covering the whole window?"))
            .choice("window", q -> q.asking("Apply the illustrative window policy to this one ramp")
                    .option("candidate", "Complete and within the fictional test bands")
                    .option("review", "Missing, stale, or incomplete data")
                    .option("defer", "Complete data but a fictional test band or timing is exceeded"))
            .rating("effort", q -> q.asking("Rate the illustrative planning effort for this proposed window")
                    .level("easy", "All fictional test bands satisfied")
                    .level("unknown", "Readings need review")
                    .level("hard", "A fictional band or timing is exceeded"))
            .build();
    private static final ClassificationSpec WATERWAY = ClassificationSpec.builder()
            .asking("Classify the named waterway in this record")
            .category("intracoastal", "A synthetic intracoastal route setting")
            .category("ocean", "A synthetic ocean route setting")
            .build();

    @Action
    public RampBriefs gather(CompareRequest request) {
        if (request.live()) return gatherLive(request);
        var helper = new PaddleDayAgent();
        var catalog = SyntheticCityCatalog.places(request.city());
        List<RampBrief> briefs = new ArrayList<>();
        for (var input : request.ramps()) {
            var place = catalog.stream().filter(d -> d.waterway().equals(input.waterway()))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown waterway: " + input.waterway()));
            Instant start = request.windowStartUtc().plusSeconds(place.windowOffsetMinutes() * 60L);
            Instant end = request.windowEndUtc().plusSeconds(place.windowOffsetMinutes() * 60L);
            var single = new PaddleDayAgent.PlanningRequest(input.conditions(), request.localDate(), input.waterway(),
                    request.asOfUtc(), request.departureUtc(), start, end,
                    request.predictionTimeUtc().plusSeconds(place.windowOffsetMinutes() * 60L), request.syntheticDaylightEndUtc());
            Integer delay = input.trafficDelayMinutes();
            Instant arrival = delay == null || input.baseDriveMinutes() == null ? null
                    : request.departureUtc().plusSeconds((input.baseDriveMinutes() + delay) * 60L);
            var drive = new PaddleDayAgent.Drive(input.baseDriveMinutes(), delay, arrival);
            var destination = new PaddleDayAgent.Destination(place.name(), place.waterway());
            String text = "Requested city: " + SyntheticCityCatalog.normalizeCity(request.city()) + "\n"
                    + "Demo route setting: " + place.routeSetting() + "\n"
                    + helper.prepare(single, destination, helper.season(single), helper.weather(single),
                    helper.tides(single), drive).text();
            briefs.add(new RampBrief(place, input, start, end, text, false, null));
        }
        return new RampBriefs(List.copyOf(briefs));
    }

    private RampBriefs gatherLive(CompareRequest request) {
        var found = liveProvider.search(request.city(), request.windowStartUtc(), request.origin());
        var helper = new PaddleDayAgent();
        List<RampBrief> briefs = new ArrayList<>();
        for (int i=0; i<found.options().size(); i++) {
            var option = found.options().get(i);
            var forecast = option.forecast();
            Instant start = request.windowStartUtc().plusSeconds(i * 3600L);
            Instant end = request.windowEndUtc().plusSeconds(i * 3600L);
            var place = new SyntheticCityCatalog.Place(option.place().name(), "waterway unverified", "unverified", i * 60);
            var conditions = new PaddleDayAgent.Conditions(option.place().sourceUrl(), null, null,
                    forecast.windKt(), forecast.gustKt(), forecast.waveFt(), null, null, null,
                    forecast.oceanCurrentKt());
            var input = new RampInput(option.place().sourceUrl(), conditions,
                    request.simulatedTrafficDelayMinutes(), option.driveMinutes());
            var single = new PaddleDayAgent.PlanningRequest(conditions, request.localDate(), place.waterway(),
                    request.asOfUtc(), request.departureUtc(), start, end, start, request.syntheticDaylightEndUtc());
            String noaaText = noaaText(option.noaaContext());
            Integer delay = request.simulatedTrafficDelayMinutes();
            Instant arrival = option.driveMinutes() == null || delay == null ? null
                    : request.departureUtc().plusSeconds((option.driveMinutes() + delay) * 60L);
            String text = "Requested destination: " + request.city() + "\nResolved location: " + found.location().label()
                    + "\nResolved city coordinates: " + found.location().latitude() + "," + found.location().longitude()
                    + "\nGeocoding source: " + found.location().sourceUrl()
                    + "\nMapped place source: " + option.place().sourceUrl()
                    + "\nMapped place coordinates: " + option.place().latitude() + "," + option.place().longitude()
                    + "\nDemo route setting: unverified\nAccess and actual waterway: unverified\n"
                    + "Forecast retrieval UTC: " + found.retrievedAtUtc()
                    + "\nForecast sample UTC: " + forecast.forecastHourUtc()
                    + "\nWeather model grid coordinates: " + forecast.weatherGridLatitude() + "," + forecast.weatherGridLongitude()
                    + "\nMarine model grid coordinates: " + forecast.marineGridLatitude() + "," + forecast.marineGridLongitude()
                    + "\nWeather source: " + forecast.weatherUrl() + "\nMarine source: " + forecast.marineUrl()
                    + "\nSea level height above mean sea level (m): " + forecast.seaLevelMslMeters()
                    + "\nThis height is NOT an MLLW tide prediction or a navigation datum. "
                    + noaaText + "\nOSRM base driving minutes: " + option.driveMinutes()
                    + "; simulated traffic delay minutes: " + delay + "; live traffic is unknown.\n"
                    + helper.prepare(single, new PaddleDayAgent.Destination(place.name(), place.waterway()),
                    helper.season(single), helper.weather(single), helper.tides(single),
                    new PaddleDayAgent.Drive(option.driveMinutes(), delay, arrival)).text();
            briefs.add(new RampBrief(place, input, start, end, text, true, option.noaaContext()));
        }
        return new RampBriefs(List.copyOf(briefs));
    }

    private static String noaaText(NoaaCoopsProvider.Context context) {
        if (context == null) return "NOAA CO-OPS: unavailable";
        String tide = context.nearestTide() == null ? "none" : context.nearestTide().name()
                + " (" + context.nearestTide().id() + ", "
                + String.format(java.util.Locale.ROOT, "%.2f", context.nearestTide().distanceKm())
                + " km from mapped place, type " + context.nearestTide().type() + ")";
        String current = context.nearestCurrent() == null ? "none" : context.nearestCurrent().name()
                + " (" + context.nearestCurrent().id() + ", "
                + String.format(java.util.Locale.ROOT, "%.2f", context.nearestCurrent().distanceKm())
                + " km from mapped place, bin " + context.nearestCurrent().bin() + ")";
        String events = context.tideEvents().stream().map(e -> e.type() + " " + e.timeUtc()
                + " " + e.heightFtMllw() + " ft MLLW at station")
                .collect(java.util.stream.Collectors.joining("; "));
        return "NOAA nearest tide station: " + tide + "\nNOAA tide status: " + context.tideStatus()
                + "\nNOAA station high/low predictions: " + (events.isBlank() ? "unavailable" : events)
                + "\nNOAA tide source: " + context.tidePredictionUrl()
                + "\nNOAA nearest current station: " + current
                + "\nNOAA current status: " + context.currentStatus()
                + "\nThese station values do not establish launch water depth, local current, or safe access.";
    }

    @Action
    public Assessments assess(RampBriefs briefs, Ai ai) {
        var service = ai.decisions().named("activeDecisionService");
        var classifier = ai.classifications().named("activeDecisionService");
        List<RampAssessment> results = new ArrayList<>();
        for (var brief : briefs.values()) {
            long decisionStart = System.nanoTime();
            var response = service.ask(DecisionRequest.of(brief.text(), QUESTIONS));
            long decisionNanos = System.nanoTime() - decisionStart;
            var p = ((DecisionAnswer.Proposition) response.answer("complete")).getOutcome();
            var c = ((DecisionAnswer.Choice) response.answer("window")).getOutcome();
            var r = ((DecisionAnswer.Rating) response.answer("effort")).getOutcome();
            long classificationStart = System.nanoTime();
            var cls = classifier.classify(ClassificationRequest.of(brief.text(), WATERWAY));
            long classificationNanos = System.nanoTime() - classificationStart;
            results.add(new RampAssessment(brief.place(), brief.input(), brief.windowStartUtc(), brief.windowEndUtc(),
                    CoastalPolicy.evaluate(brief.text()), brief.live(), brief.noaaContext(),
                    propositionValue(p), classificationValue(c), ratingValue(r), ratingProjection(r),
                    ratingEvidence(r), classificationValue(cls),
                    provenance(p instanceof PropositionResult.Answered a ? a.getProvenance() : null),
                    provenance(c instanceof ClassificationResult.Selected s ? s.getProvenance() : null),
                    provenance(r instanceof RatingResult.Answered a ? a.getProvenance() : null),
                    provenance(cls instanceof ClassificationResult.Selected s ? s.getProvenance() : null),
                    decisionNanos, classificationNanos));
        }
        return new Assessments(List.copyOf(results));
    }

    private static String propositionValue(PropositionResult result) {
        if (result instanceof PropositionResult.Answered a) return Boolean.toString(a.getAnswer());
        if (result instanceof PropositionResult.Failure f) return "failure:" + f.getReason();
        return "inconclusive";
    }

    private static String classificationValue(ClassificationResult result) {
        if (result instanceof ClassificationResult.Selected s) return s.getCategoryId();
        if (result instanceof ClassificationResult.Failure f) return "failure:" + f.getReason();
        return result.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
    }

    private static String ratingValue(RatingResult result) {
        if (result instanceof RatingResult.Answered a) return a.getSelectedLevelId() == null ? "answered-no-level" : a.getSelectedLevelId();
        if (result instanceof RatingResult.Failure f) return "failure:" + f.getReason();
        return "inconclusive";
    }

    /** A local, conservative projection for providers that report a distribution without a selected level. */
    static String ratingProjection(RatingResult result) {
        if (!(result instanceof RatingResult.Answered a)) return ratingValue(result);
        if (a.getSelectedLevelId() != null) return a.getSelectedLevelId();
        var top = a.getDistribution().stream()
                .max(java.util.Comparator.comparingDouble(com.embabel.common.ai.decision.LevelProbability::getProbability));
        return top.filter(level -> level.getProbability() > 0.5)
                .map(com.embabel.common.ai.decision.LevelProbability::getLevelId)
                .orElse("no-majority-level");
    }

    private static String ratingEvidence(RatingResult result) {
        if (!(result instanceof RatingResult.Answered a)) return "none";
        String distribution = a.getDistribution().stream()
                .map(level -> level.getLevelId() + ":" + String.format(java.util.Locale.ROOT, "%.3f", level.getProbability()))
                .collect(java.util.stream.Collectors.joining(";"));
        return distribution.isEmpty() ? "none" : distribution;
    }

    private static String provenance(ModelProvenance model) {
        return model == null ? "none" : model.getProvider() + "/" + model.getModelName()
                + "/" + (model.getVersion() == null ? "unreported" : model.getVersion());
    }

    @Action
    public PreferenceProfile preferences(CompareRequest request) {
        return personaService.read(request.preferredWaterwayType());
    }

    @Action
    public RankedRamps rank(Assessments assessments, PreferenceProfile profile) {
        List<RampCandidate> eligible = assessments.values().stream()
                .filter(a -> a.localPolicy().equals("candidate") && a.choice().equals("candidate")
                        && a.proposition().equals("true") && a.ratingProjection().equals("easy")
                        && (a.classification().equals("intracoastal") || a.classification().equals("ocean")))
                .map(RampCandidate::new).toList();
        boolean provisional = assessments.values().stream().anyMatch(RampAssessment::live);
        if (provisional) eligible = assessments.values().stream()
                .filter(a -> a.live() && a.input().conditions().windKt() != null)
                .map(RampCandidate::new).toList();
        Ranker ranker = new LocalRampRanker(profile);
        long rankStart = System.nanoTime();
        var ranking = ranker.rank("eligible paddle research options", profile.preferredWaterwayType(), eligible);
        long rankNanos = System.nanoTime() - rankStart;
        List<String> names = ranking.rankings().stream().map(r -> r.getMatch().getName()).toList();
        String fallback = assessments.values().stream().anyMatch(a -> !a.localPolicy().equals("defer")) ? "review" : "defer";
        var selected = ranking.rankings().isEmpty() ? null : ((RampCandidate) ranking.rankings().getFirst().getMatch()).assessment();
        return new RankedRamps(names, selected == null ? null : selected.place().name(),
                selected == null ? null : selected.windowStartUtc(), selected == null ? null : selected.windowEndUtc(),
                fallback, provisional, profile, rankNanos);
    }

    @Action
    @AchievesGoal(description = "Produce a single candidate ramp or an explicit no-candidate card")
    public ComparisonCard finish(CompareRequest request, RankedRamps ranked, Assessments assessments) {
        return new ComparisonCard(request.live() ? request.city() : SyntheticCityCatalog.normalizeCity(request.city()), ranked.selected(),
                ranked.selectedStartUtc(), ranked.selectedEndUtc(),
                ranked.selected() == null ? ranked.fallback() : ranked.provisional() ? "research" : "candidate",
                ranked.names(), assessments.values(), ranked.profile(), ranked.rankNanos());
    }

    /** Implements Embabel's Ranker contract with transparent local scores, not a model response. */
    static final class LocalRampRanker implements Ranker {
        private final PreferenceProfile profile;
        LocalRampRanker(PreferenceProfile profile) { this.profile = profile; }
        @Override
        public <T extends Named & Described> Rankings<T> rank(String description, String userInput,
                                                               java.util.Collection<? extends T> rankables) {
            List<T> ordered = new ArrayList<>(rankables);
            ordered.sort(Comparator.comparing(Named::getName));
            List<Ranking<T>> scored = new ArrayList<>();
            for (T item : ordered) {
                if (!(item instanceof RampCandidate candidate)) throw new IllegalArgumentException("RampCandidate required");
                var a = candidate.assessment();
                var c = a.input().conditions();
                double score = 0.7 - (c.windKt() == null ? 0.2 : c.windKt() * profile.lowWindWeight())
                        - (c.tidalCurrentKt() == null ? 0.1 : c.tidalCurrentKt() / 10.0)
                        - (a.input().trafficDelayMinutes() == null ? 0.1 : a.input().trafficDelayMinutes() * profile.shortDriveWeight())
                        - (a.input().baseDriveMinutes() == null ? 0.1 : a.input().baseDriveMinutes() * 0.002)
                        + (c.waterHeightFtMllw() == null ? 0 : Math.min(c.waterHeightFtMllw() - 2.0, 2.0) * 0.02)
                        + (a.classification().equalsIgnoreCase(userInput) ? profile.preferredTypeBonus() : 0.0);
                scored.add(new Ranking<>(item, Math.max(0.0, Math.min(1.0, score))));
            }
            scored.sort(Comparator.comparingDouble((Ranking<T> r) -> r.getScore()).reversed()
                    .thenComparing(r -> r.getMatch().getName()));
            return new Rankings<>(scored);
        }
    }
}
