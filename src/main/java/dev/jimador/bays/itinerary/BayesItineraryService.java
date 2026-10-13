package dev.jimador.bays.itinerary;

import dev.jimador.bays.decision.BayesDecisionServices;
import dev.jimador.bays.decision.DecisionInputHash;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcessStatusCode;
import com.embabel.agent.core.ProcessOptions;
import java.util.List;
import org.springframework.stereotype.Service;

/** Runs Tripper-shaped itinerary actions only after upfront requirements are complete. */
@Service
public class BayesItineraryService {
    public record Answer(String completeness, String choice, String rating, String classification,
                         String model, String inputSha256, double decisionMs, double classificationMs,
                         int followupCount) {}
    public record Result(String status, String city, String date, String selectedAlternative,
                         List<ItineraryPlanner.Alternative> alternatives, Answer decision,
                         String memoryContext, List<String> memoryFactIds, String rememberedInterests,
                         String rememberedPace, List<String> researchSources, String retrievedUtc,
                         List<String> questions, long processMs,
                         List<BayesAgent.ResearchedPointOfInterest> researchedPlaces,
                         String destinationTimezone) {}

    public static class ItineraryUnavailableException extends RuntimeException {}
    public static class DecisionResponseException extends RuntimeException {}

    private final AgentPlatform platform;
    private final BayesDecisionServices decisions;
    private final ItineraryMemory memory;
    public BayesItineraryService(AgentPlatform platform, BayesDecisionServices decisions, ItineraryMemory memory) {
        this.platform = platform; this.decisions = decisions; this.memory = memory;
    }

    public Result plan(ItineraryRequest request) {
        request.validateShape();
        if (request.useRememberedPreferences() && memory.previous(request.profileId()).isEmpty())
            return new Result("needs_input", request.city(), request.date() == null ? null : request.date().toString(),
                    null, List.of(), null, null, List.of(), null, null, List.of(), null,
                    List.of("No saved preferences were found for this profile. Enter interests and pace, then choose to save them."), 0, List.of(), null);
        var effective = memory.resolve(request);
        List<String> questions = effective.questions();
        if (!questions.isEmpty()) return new Result("needs_input", request.city(),
                request.date() == null ? null : request.date().toString(), null, List.of(), null,
                null, List.of(), null, null, List.of(), null, questions, 0, List.of(), null);
        if (!request.remoteModelConsent()) return new Result("needs_input", request.city(),
                request.date().toString(), null, List.of(), null, null, List.of(), null, null,
                List.of(), null, List.of("Confirm sharing city, date, group, interests, pace, cuisine likes/dislikes, and access requirements with OpenAI for research and planning. Exact origin and allergy text stay out of model prompts."),
                0, List.of(), null);
        if (System.getenv("OPENAI_API_KEY") == null || System.getenv("OPENAI_API_KEY").isBlank())
            throw new BayesDecisionServices.ModelUnavailableException(
                    "OPENAI_API_KEY is required for Tripper-style research and planning");
        decisions.forItineraryEngine(request.engine());
        var agent = platform.agents().stream().filter(a -> a.getName().contains("BayesAgent"))
                .findFirst().orElseThrow(ItineraryUnavailableException::new);
        long started = System.nanoTime();
        var process = platform.createAgentProcessFrom(agent, new ProcessOptions(), effective).run();
        long elapsed = (System.nanoTime() - started) / 1_000_000;
        if (process.getStatus() != AgentProcessStatusCode.COMPLETED)
            throw new ItineraryUnavailableException();
        var plan = process.last(BayesAgent.TravelPlan.class);
        if (plan == null) throw new ItineraryUnavailableException();
        if (plan.status().equals("decision_failed") || plan.review().choice().equals("inconclusive")
                || plan.review().rating().equals("unselected"))
            throw new DecisionResponseException();
        var review = plan.review();
        Answer answer = new Answer(review.completeness(), review.choice(), review.rating(),
                review.classification(), review.model(), DecisionInputHash.sha256(review.decisionInput()),
                review.decisionNanos() / 1_000_000.0, review.classificationNanos() / 1_000_000.0,
                review.followupCount());
        List<String> open = plan.alternatives().stream().filter(a -> a.id().equals(plan.selectedAlternative()))
                .findFirst().map(ItineraryPlanner.Alternative::openQuestions).orElseGet(() ->
                        plan.alternatives().stream().flatMap(a -> a.openQuestions().stream()).distinct().toList());
        return new Result(plan.status(), plan.city(), plan.date(), plan.selectedAlternative(),
                plan.alternatives(), answer, plan.memory().context(), plan.memory().factIds(),
                plan.memory().interests(), plan.memory().pace(), plan.researchSources(),
                plan.retrievedUtc(), open, elapsed, plan.researchedPlaces(), plan.destinationTimezone());
    }
}
