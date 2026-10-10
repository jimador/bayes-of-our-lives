package dev.jimador.decisiondesk;

import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.common.Ai;
import com.embabel.common.ai.classification.ClassificationResult;
import com.embabel.common.ai.decision.DecisionAnswer;
import com.embabel.common.ai.decision.DecisionSpec;

@Agent(description = "Turn a synthetic kayaking preference note into a personal day card")
public class PaddleDayAgent {
    public record Note(String id, String text) {}
    public record Preference(String id, String choice, String status, long decisionNanos, String model) {}
    public record Draft(String id, String choice, String status, long decisionNanos, String model, String[] nextSteps) {}
    public record DayCard(String id, String choice, String status, long decisionNanos, String model, String[] nextSteps) {}

    private static final DecisionSpec PREFERENCE = DecisionSpec.builder()
            .choice("day", q -> q.asking("What does this person want to do with kayaking today? Classify their stated preference, not weather or water safety.")
                    .option("paddle", "They want to paddle today and have time for it")
                    .option("plan", "They want to research or plan a future kayak outing, but not paddle today")
                    .option("rest", "They want a day off from kayaking and kayak planning"))
            .build();

    @Action
    public Preference choose(Note note, Ai ai) {
        long start = System.nanoTime();
        var response = ai.decisions().named("activeDecisionService").ask(note.text(), PREFERENCE);
        long nanos = System.nanoTime() - start;
        var result = ((DecisionAnswer.Choice) response.answer("day")).getOutcome();
        if (result instanceof ClassificationResult.Selected selected) {
            return new Preference(note.id(), selected.getCategoryId(), "selected", nanos,
                    selected.getProvenance().getModelName());
        }
        return new Preference(note.id(), "review", result.getClass().getSimpleName(), nanos, "none");
    }

    @Action
    public Draft prepare(Preference preference) {
        String[] steps = switch (preference.choice()) {
            case "paddle" -> new String[] {"Check current local conditions and access rules", "Make your own go/no-go decision", "Put a possible outing on today's calendar"};
            case "plan" -> new String[] {"Save a possible route for another day", "Check access details", "Pick a date to revisit the idea"};
            case "rest" -> new String[] {"Leave the kayak gear alone", "Keep the day open for something else"};
            default -> new String[] {"Review the note yourself before making a plan"};
        };
        return new Draft(preference.id(), preference.choice(), preference.status(), preference.decisionNanos(), preference.model(), steps);
    }

    @Action
    @AchievesGoal(description = "Produce a personal kayaking day card")
    public DayCard finish(Draft draft) {
        return new DayCard(draft.id(), draft.choice(), draft.status(), draft.decisionNanos(), draft.model(), draft.nextSteps());
    }
}
