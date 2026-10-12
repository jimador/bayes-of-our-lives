package dev.jimador.bays;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcessStatusCode;
import com.embabel.agent.core.ProcessOptions;
import java.util.List;
import org.springframework.stereotype.Service;

/** Runs Tripper-shaped itinerary actions only after upfront requirements are complete. */
@Service
public class BayesItineraryService {
    public record Answer(String completeness, String choice, String rating, String classification,
                         String model, String inputSha256, double decisionMs, double classificationMs) {}
    public record Result(String status, String city, String date, String selectedAlternative,
                         List<ItineraryPlanner.Alternative> alternatives, Answer decision,
                         String memoryContext, List<String> memoryFactIds, String rememberedInterests,
                         String rememberedPace, List<String> researchSources, String retrievedUtc,
                         List<String> questions, long processMs) {}

    private final AgentPlatform platform;
    private final BayesDecisionServices decisions;
    public BayesItineraryService(AgentPlatform platform, BayesDecisionServices decisions) {
        this.platform = platform; this.decisions = decisions;
    }

    public Result plan(ItineraryRequest request) {
        request.validateShape();
        List<String> questions = request.questions();
        if (!questions.isEmpty()) return new Result("needs_input", request.city(),
                request.date() == null ? null : request.date().toString(), null, List.of(), null,
                null, List.of(), null, null, List.of(), null, questions, 0);
        decisions.forItineraryEngine(request.engine());
        var agent = platform.agents().stream().filter(a -> a.getName().contains("BayesAgent"))
                .findFirst().orElseThrow(BayesGuideService.GuideUnavailableException::new);
        long started = System.nanoTime();
        var process = platform.createAgentProcessFrom(agent, new ProcessOptions(), request).run();
        long elapsed = (System.nanoTime() - started) / 1_000_000;
        if (process.getStatus() != AgentProcessStatusCode.COMPLETED)
            throw new BayesGuideService.GuideUnavailableException();
        var plan = process.last(BayesAgent.TravelPlan.class);
        if (plan == null) throw new BayesGuideService.GuideUnavailableException();
        if (plan.status().equals("decision_failed") || plan.review().choice().equals("inconclusive")
                || plan.review().rating().equals("unselected"))
            throw new BayesGuideService.DecisionResponseException();
        var review = plan.review();
        Answer answer = new Answer(review.completeness(), review.choice(), review.rating(),
                review.classification(), review.model(), BaysRunner.sha256(review.decisionInput()),
                review.decisionNanos() / 1_000_000.0, review.classificationNanos() / 1_000_000.0);
        List<String> open = plan.alternatives().stream().filter(a -> a.id().equals(plan.selectedAlternative()))
                .findFirst().map(ItineraryPlanner.Alternative::openQuestions).orElseGet(() ->
                        plan.alternatives().stream().flatMap(a -> a.openQuestions().stream()).distinct().toList());
        return new Result(plan.status(), plan.city(), plan.date(), plan.selectedAlternative(),
                plan.alternatives(), answer, plan.memory().context(), plan.memory().factIds(),
                plan.memory().interests(), plan.memory().pace(), plan.researchSources(),
                plan.retrievedUtc(), open, elapsed);
    }
}
