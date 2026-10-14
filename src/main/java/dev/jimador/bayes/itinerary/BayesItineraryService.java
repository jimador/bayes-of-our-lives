package dev.jimador.bayes.itinerary;

import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.api.annotation.support.AgentMetadataReader;
import com.embabel.agent.core.AgentScope;
import com.embabel.agent.core.AgentProcessStatusCode;
import com.embabel.agent.core.ProcessOptions;
import com.embabel.common.ai.decision.DecisionService;
import java.util.List;

/** Runs the known injected agent after consent and upfront requirements are complete. */
public class BayesItineraryService {
    public record Answer(String coverage, String choice, String rating, String classification,
                         String model, double decisionMs, double classificationMs) {}
    public record Result(String status, String city, String date, String selectedAlternative,
                         List<Itinerary.Alternative> alternatives, Answer decision,
                         String memoryContext, List<String> memoryFactIds, String rememberedInterests,
                         String rememberedPace, List<String> researchSources, String retrievedUtc,
                         List<String> questions, long processMs,
                         List<ResearchFindings.PlaceView> researchedPlaces,
                         String destinationTimezone) {}

    public static class ModelUnavailableException extends RuntimeException {
        public ModelUnavailableException(String message) { super(message); }
    }

    public static class ItineraryUnavailableException extends RuntimeException {}
    public static class DecisionResponseException extends RuntimeException {}

    private final AgentPlatform platform;
    private final DecisionService decisions;
    private final ItineraryMemories memory;
    private final com.embabel.agent.core.Agent agent;
    private final BayesAgent bayesAgent;
    public BayesItineraryService(AgentPlatform platform, DecisionService decisions, ItineraryMemories memory,
            BayesAgent bayesAgent, AgentMetadataReader reader) {
        this.platform = platform;
        this.decisions = decisions;
        this.memory = memory;
        this.bayesAgent = bayesAgent;
        AgentScope scope = reader.createAgentMetadata(bayesAgent);
        if (!(scope instanceof com.embabel.agent.core.Agent known))
            throw new IllegalStateException("Bayes agent metadata is unavailable");
        this.agent = known;
    }

    public Result plan(ItineraryRequest request) {
        long started = System.nanoTime();
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
        var outing = bayesAgent.prepare(effective);
        var process = platform.createAgentProcessFrom(agent, new ProcessOptions(), outing,
                new BayesAgent.SelectedDecision(decisions)).run();
        long elapsed = (System.nanoTime() - started) / 1_000_000;
        if (process.getStatus() != AgentProcessStatusCode.COMPLETED)
            throw new ItineraryUnavailableException();
        var plan = process.last(BayesAgent.TravelPlan.class);
        if (plan == null) throw new ItineraryUnavailableException();
        if (plan.status().equals("decision_failed") || plan.review().choice().equals("inconclusive")
                || plan.review().rating().equals("unselected"))
            throw new DecisionResponseException();
        var review = plan.review();
        Answer answer = new Answer(review.coverage(), review.choice(), review.rating(),
                review.classification(), review.model(),
                review.decisionMs(), review.classificationMs());
        List<String> open = plan.alternatives().stream().filter(a -> a.id().equals(plan.selectedAlternative()))
                .findFirst().map(Itinerary.Alternative::openQuestions).orElseGet(() ->
                        plan.alternatives().stream().flatMap(a -> a.openQuestions().stream()).distinct().toList());
        return new Result(plan.status(), plan.city(), plan.date(), plan.selectedAlternative(),
                plan.alternatives(), answer, plan.memory().context(), plan.memory().factIds(),
                plan.memory().interests(), plan.memory().pace(), plan.researchSources(),
                plan.retrievedUtc(), open, elapsed, plan.researchedPlaces(), plan.destinationTimezone());
    }
}
