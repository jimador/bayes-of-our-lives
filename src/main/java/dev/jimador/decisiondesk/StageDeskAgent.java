package dev.jimador.decisiondesk;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.Ai;
import com.embabel.common.ai.classification.ClassificationResult;
import com.embabel.common.ai.decision.DecisionSpec;
import org.springframework.stereotype.Component;

@Agent(description = "Prepare a practical dispatch card for a community theater stage problem")
public class StageDeskAgent {
    public record Ticket(String id, String note) {}
    public record Route(String id, String note, String crew, String status, long decisionNanos, String model) {}
    public record Plan(String id, String crew, String status, long decisionNanos, String model, String[] checklist) {}
    public record DispatchCard(String id, String crew, String status, long decisionNanos, String model, String[] checklist) {}

    private static final DecisionSpec ROUTE = DecisionSpec.builder()
            .choice("crew", q -> q.asking("Which crew should handle this theater issue first? Choose the primary crew.")
                    .option("props", "Props and set pieces: scenery, costumes, practical effects")
                    .option("sound", "Sound: speakers, microphones, monitors, audio feedback")
                    .option("safety", "Safety: blocked exits, fire, injury, trip or electrical hazards"))
            .build();

    @Action
    public Route route(Ticket ticket, Ai ai) {
        long start = System.nanoTime();
        var response = ai.decisions().named("activeDecisionService").ask(ticket.note(), ROUTE);
        long nanos = System.nanoTime() - start;
        var result = ((com.embabel.common.ai.decision.DecisionAnswer.Choice) response.answer("crew")).getOutcome();
        if (result instanceof ClassificationResult.Selected selected) {
            return new Route(ticket.id(), ticket.note(), selected.getCategoryId(), "selected", nanos,
                    selected.getProvenance().getModelName());
        }
        // Operational failures and uncertainty never become a made-up crew assignment.
        return new Route(ticket.id(), ticket.note(), "human", result.getClass().getSimpleName(), nanos, "none");
    }

    @Action
    public Plan prepare(Route route) {
        String[] steps = switch (route.crew()) {
            case "safety" -> new String[] {"Stop the affected activity", "Clear the hazard", "Have the stage manager verify before resuming"};
            case "sound" -> new String[] {"Mute the affected channel", "Check the signal path", "Run a short sound check"};
            case "props" -> new String[] {"Secure the piece offstage", "Inspect or replace it", "Confirm the handoff cue"};
            default -> new String[] {"Ask the stage manager to triage this ticket"};
        };
        return new Plan(route.id(), route.crew(), route.status(), route.decisionNanos(), route.model(), steps);
    }

    @Action
    @AchievesGoal(description = "Produce a dispatch card with the crew and first actions")
    public DispatchCard finish(Plan plan) {
        return new DispatchCard(plan.id(), plan.crew(), plan.status(), plan.decisionNanos(), plan.model(), plan.checklist());
    }
}
