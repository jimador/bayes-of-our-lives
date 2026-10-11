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
import com.embabel.common.ai.decision.DecisionAnswer;
import com.embabel.common.ai.decision.DecisionRequest;
import com.embabel.common.ai.decision.DecisionSpec;
import com.embabel.common.ai.decision.PropositionResult;
import com.embabel.common.ai.decision.RatingResult;
import com.embabel.common.core.types.Described;
import com.embabel.common.core.types.Named;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

/** A museum afternoon: sourced research, fictional preferences, swappable decisions. */
@Agent(description = "Research museum options for an afternoon in an unfamiliar city")
public class CityAfternoonAgent {
    public record Request(String city, String origin, Instant startUtc, Integer simulatedTrafficDelayMinutes) {}
    public record Brief(CityOutingProvider.Option option, String text, String localPolicy) {}
    public record Briefs(String resolvedCity, String geocodingUrl, Instant startUtc,
                         Instant retrievedUtc, List<Brief> values) {}
    public record Assessment(CityOutingProvider.Option option, String localPolicy, String proposition,
                             String choice, String rating, String classification, String model,
                             long decisionNanos, long classificationNanos) {}
    public record Assessments(String resolvedCity, String geocodingUrl, Instant startUtc,
                              Instant retrievedUtc, List<Assessment> values) {}
    public record Preferences(PaddleComparisonAgent.PreferenceProfile value) {}
    public record Scored(Assessment assessment) implements Named, Described {
        @Override public String getName() { return assessment.option().place().name(); }
        @Override public String getDescription() { return assessment.option().place().url(); }
    }
    public record RankingResult(List<String> names, String first, long rankNanos, Preferences preferences) {}
    public record Plan(String city, String selectedResearchLead, String status, Instant startUtc,
                       List<String> rankedOptions, List<Assessment> assessments, String geocodingUrl,
                       Instant retrievedUtc, Preferences preferences, long rankNanos) {}

    @Autowired private CityOutingProvider provider;
    @Autowired private DicePersonaService persona;

    private static final DecisionSpec QUESTIONS = DecisionSpec.builder()
            .proposition("complete", q -> q.asking("Are temperature, precipitation probability, and base drive estimate present for this museum option?"))
            .choice("outing", q -> q.asking("Apply the explicitly fictional museum-afternoon policy in the brief")
                    .option("candidate", "Complete; precipitation probability at most 50 percent and estimated drive at most 45 minutes including simulated delay")
                    .option("review", "A required reading or route is missing")
                    .option("defer", "Complete but rain or drive exceeds a fictional threshold"))
            .rating("effort", q -> q.asking("Rate the fictional effort of this museum option")
                    .level("easy", "Meets both fictional thresholds")
                    .level("unknown", "Missing reading or route")
                    .level("hard", "Exceeds a fictional threshold"))
            .build();
    private static final ClassificationSpec DISTANCE = ClassificationSpec.builder()
            .asking("Classify the base drive estimate in the brief")
            .category("nearby", "Base drive at most 30 minutes")
            .category("long-drive", "Base drive over 30 minutes or unavailable")
            .build();

    @Action public Briefs discover(Request request) {
        if (request.simulatedTrafficDelayMinutes() == null || request.simulatedTrafficDelayMinutes() < 0
                || request.simulatedTrafficDelayMinutes() > 180)
            throw new IllegalArgumentException("Simulated traffic delay must be 0..180 minutes");
        var found = provider.search(request.city(), request.origin(), request.startUtc());
        List<Brief> values = new ArrayList<>();
        for (var option : found.options()) {
            Integer base = option.baseDriveMinutes();
            Integer total = base == null ? null : base + request.simulatedTrafficDelayMinutes();
            String policy = option.temperatureC() == null || option.precipitationProbability() == null || total == null
                    ? "review" : option.precipitationProbability() > 50 || total > 45 ? "defer" : "candidate";
            String category = base != null && base <= 30 ? "nearby" : "long-drive";
            String text = "City: " + found.resolvedCity() + "\nMuseum lead: " + option.place().name()
                    + "\nMapped source: " + option.place().url()
                    + "\nForecast sample UTC: " + option.sampleUtc() + "\nForecast source: " + option.weatherUrl()
                    + "\nTemperature C: " + option.temperatureC()
                    + "\nPrecipitation probability percent: " + option.precipitationProbability()
                    + "\nWind knots: " + option.windKt()
                    + "\nBase drive estimate minutes: " + base
                    + "\nSimulated traffic delay minutes: " + request.simulatedTrafficDelayMinutes()
                    + "\nTotal illustrative drive minutes: " + total
                    + "\nOuting category: " + category + "\nOuting policy: " + policy
                    + "\nOpening hours, admission, accessibility and real-time traffic: unverified."
                    + " The thresholds are fictional, and candidate means a research lead, not a confirmed visit.";
            values.add(new Brief(option, text, policy));
        }
        return new Briefs(found.resolvedCity(), found.geocodingUrl(), found.startUtc(),
                found.retrievedUtc(), List.copyOf(values));
    }

    @Action public Assessments assess(Briefs briefs, Ai ai) {
        var decisions = ai.decisions().named("activeDecisionService");
        var classifications = ai.classifications().named("activeDecisionService");
        List<Assessment> values = new ArrayList<>();
        for (var brief : briefs.values()) {
            long start = System.nanoTime();
            var response = decisions.ask(DecisionRequest.of(brief.text(), QUESTIONS));
            long decisionNanos = System.nanoTime() - start;
            var p = ((DecisionAnswer.Proposition) response.answer("complete")).getOutcome();
            var c = ((DecisionAnswer.Choice) response.answer("outing")).getOutcome();
            var r = ((DecisionAnswer.Rating) response.answer("effort")).getOutcome();
            start = System.nanoTime();
            var cls = classifications.classify(ClassificationRequest.of(brief.text(), DISTANCE));
            long classificationNanos = System.nanoTime() - start;
            String model = p instanceof PropositionResult.Answered answer && answer.getProvenance() != null
                    ? answer.getProvenance().getProvider() + "/" + answer.getProvenance().getModelName() : "unreported";
            values.add(new Assessment(brief.option(), brief.localPolicy(),
                    p instanceof PropositionResult.Answered answer ? Boolean.toString(answer.getAnswer()) : "inconclusive",
                    classificationValue(c),
                    PaddleComparisonAgent.ratingProjection(r),
                    classificationValue(cls),
                    model, decisionNanos, classificationNanos));
        }
        return new Assessments(briefs.resolvedCity(), briefs.geocodingUrl(), briefs.startUtc(),
                briefs.retrievedUtc(), List.copyOf(values));
    }

    private static String classificationValue(ClassificationResult result) {
        if (result instanceof ClassificationResult.Selected selected) return selected.getCategoryId();
        if (result instanceof ClassificationResult.Failure failure) return "failure:" + failure.getReason();
        return result.getClass().getSimpleName().toLowerCase(java.util.Locale.ROOT);
    }

    @Action public Preferences preferences(Request request) {
        return new Preferences(persona.read("intracoastal"));
    }

    @Action public RankingResult rank(Assessments assessments, Preferences preferences) {
        List<Scored> options = assessments.values().stream()
                .filter(a -> a.proposition().equals("true") && a.choice().equals("candidate")
                        && a.rating().equals("easy")
                        && (a.classification().equals("nearby") || a.classification().equals("long-drive")))
                .map(Scored::new).toList();
        // If all decision answers fail or decline, the card names no research lead.
        Ranker ranker = new OutingRanker(preferences.value());
        long start = System.nanoTime();
        var rankings = ranker.rank("sourced museum research options", "short drive and modest weather", options);
        long elapsed = System.nanoTime() - start;
        List<String> names = rankings.rankings().stream().map(r -> r.getMatch().getName()).toList();
        return new RankingResult(names, names.isEmpty() ? null : names.getFirst(), elapsed, preferences);
    }

    @Action @AchievesGoal(description = "Return a ranked, sourced museum-afternoon research plan")
    public Plan finish(Request request, Assessments assessments, RankingResult ranking) {
        return new Plan(assessments.resolvedCity(), ranking.first(), "research", assessments.startUtc(),
                ranking.names(), assessments.values(), assessments.geocodingUrl(), assessments.retrievedUtc(),
                ranking.preferences(), ranking.rankNanos());
    }

    static final class OutingRanker implements Ranker {
        private final PaddleComparisonAgent.PreferenceProfile profile;
        OutingRanker(PaddleComparisonAgent.PreferenceProfile profile) { this.profile = profile; }
        @Override public <T extends Named & Described> Rankings<T> rank(String description, String userInput,
                                                                         java.util.Collection<? extends T> rankables) {
            List<Ranking<T>> scores = new ArrayList<>();
            for (T item : rankables) {
                if (!(item instanceof Scored scored)) throw new IllegalArgumentException("Scored option required");
                var o = scored.assessment().option();
                double score = 1.0 - (o.baseDriveMinutes() == null ? 0.25 : o.baseDriveMinutes() * profile.shortDriveWeight())
                        - (o.precipitationProbability() == null ? 0.25 : o.precipitationProbability() / 250.0)
                        - (o.windKt() == null ? 0.05 : o.windKt() * profile.lowWindWeight());
                scores.add(new Ranking<>(item, Math.max(0, Math.min(1, score))));
            }
            scores.sort(Comparator.comparingDouble((Ranking<T> r) -> r.getScore()).reversed()
                    .thenComparing(r -> r.getMatch().getName()));
            return new Rankings<>(scores);
        }
    }
}
