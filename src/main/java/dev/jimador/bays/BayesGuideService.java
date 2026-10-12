package dev.jimador.bays;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcessStatusCode;
import com.embabel.agent.core.ProcessOptions;
import java.util.List;
import org.springframework.stereotype.Service;

/** Executes one bounded guide request and returns only display-safe facts. */
@Service
public class BayesGuideService {
    private final AgentPlatform platform;
    private final BayesDecisionServices decisions;

    public BayesGuideService(AgentPlatform platform, BayesDecisionServices decisions) {
        this.platform = platform;
        this.decisions = decisions;
    }

    public GuideResult guide(GuideQuery query) {
        query.validate();
        decisions.forEngine(query.engine()); // Fail before planning when server credentials are absent.
        var agent = platform.agents().stream().filter(a -> a.getName().contains("BayesAgent"))
                .findFirst().orElseThrow(() -> new GuideUnavailableException());
        var request = new BayesAgent.Request(query.city(), query.intent(), query.availableMinutes(),
                query.profile(), query.mode(), query.mealPeriod(), query.engine());
        long started = System.nanoTime();
        var process = platform.createAgentProcessFrom(agent, new ProcessOptions(), request).run();
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;
        if (process.getStatus() != AgentProcessStatusCode.COMPLETED) throw new GuideUnavailableException();
        var plan = process.last(BayesAgent.Plan.class);
        if (plan == null) throw new GuideUnavailableException();
        boolean decisionFailure = plan.assessments().stream().filter(a -> a.hardStatus().equals("eligible"))
                .anyMatch(a -> failed(a.interestMatch()) || failed(a.choice()) || failed(a.rating()) || failed(a.setting()));
        if (decisionFailure) throw new DecisionResponseException();
        List<Lead> leads = plan.assessments().stream().map(a -> new Lead(a.option().name(),
                a.option().sourceUrl(), a.option().oneWayDriveMinutes(), a.hardStatus(),
                BaysRunner.sha256(a.decisionInput()), a.decisionInput(), a.interestMatch(), a.choice(),
                a.rating(), a.setting(), a.model(), a.decisionNanos() / 1_000_000.0,
                a.classificationNanos() / 1_000_000.0)).toList();
        return new GuideResult(plan.selectedResearchLead() == null ? "no_result" : "result",
                plan.selectedResearchLead(), plan.city(), query.mode(), query.intent(), query.mealPeriod(),
                query.engine(), plan.providerSource(), plan.retrievedUtc(), elapsedMs,
                plan.memoryProvenance(), plan.memoryFacts(), plan.rankedOptions(), leads);
    }

    private static boolean failed(String answer) {
        return answer == null || answer.startsWith("failure:") || answer.equals("inconclusive")
                || answer.equals("unselected") || answer.equals("unreported");
    }

    public record GuideQuery(String mode, String city, String intent, String mealPeriod, int availableMinutes,
                             String profile, String engine) {
        public void validate() {
            if (!"live".equals(mode)) throw new IllegalArgumentException("The guide uses live city lookup");
            if (!List.of("sights", "meal").contains(intent)) throw new IllegalArgumentException("Choose sights or meal");
            if (!List.of("breakfast", "lunch", "dinner").contains(mealPeriod))
                throw new IllegalArgumentException("Choose breakfast, lunch, or dinner");
            if (availableMinutes < 30 || availableMinutes > 240)
                throw new IllegalArgumentException("Available time must be 30 to 240 minutes");
            if (!List.of("art-quiet", "science-lively").contains(profile))
                throw new IllegalArgumentException("Choose a fictional traveler profile");
            if (!List.of("local", "jev", "openai", "clef").contains(engine))
                throw new IllegalArgumentException("Choose a listed decision engine");
            if (city == null || city.isBlank() || city.length() > 100 || city.chars().anyMatch(Character::isISOControl))
                throw new IllegalArgumentException("Enter a city of up to 100 characters");
        }
    }
    public record Lead(String name, String sourceUrl, Integer oneWayBaseDriveMinutes, String hardStatus,
                       String inputSha256, String decisionInput, String interestMatch, String choice,
                       String rating, String setting, String model, double decisionMs, double classificationMs) {}
    public record GuideResult(String outcome, String selectedResearchLead, String city, String mode, String intent,
                              String mealPeriod, String engine, String providerSource, String retrievedUtc,
                              long processMs, String memoryProvenance, List<GuideMemories.Fact> memoryFacts,
                              List<String> rankedOptions, List<Lead> leads) {}
    public static class GuideUnavailableException extends RuntimeException {}
    public static class DecisionResponseException extends RuntimeException {}
}
