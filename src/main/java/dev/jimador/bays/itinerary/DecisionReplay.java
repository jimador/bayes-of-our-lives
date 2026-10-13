package dev.jimador.bays.itinerary;

import com.embabel.common.ai.classification.ClassificationRequest;
import com.embabel.common.ai.classification.ClassificationSpec;
import com.embabel.common.ai.decision.DecisionAnswer;
import com.embabel.common.ai.decision.DecisionRequest;
import com.embabel.common.ai.decision.DecisionService;
import com.embabel.common.ai.decision.DecisionSpec;
import com.embabel.common.ai.decision.PropositionResult;
import dev.jimador.bays.decision.DecisionInputHash;
import java.util.List;

/** The exact immutable question and option descriptions submitted to DecisionService. */
public final class DecisionReplay {
    private DecisionReplay() {}
    public record Choice(String id, String title, String status, String type) {}
    public record Capture(String input, List<Choice> choices, String inputSha256) {
        public Capture {
            choices = List.copyOf(choices);
            if (input == null || input.isBlank() || choices.isEmpty()
                    || !DecisionInputHash.sha256(input).equals(inputSha256))
                throw new IllegalArgumentException("Replay capture has an invalid input hash or no alternatives");
        }
    }
    public record Answer(String completeness, String choice, String rating, String classification,
                         String model, double decisionMs, double classificationMs) {}

    public static Capture capture(String input, List<ItineraryPlanner.Alternative> alternatives) {
        return new Capture(input, alternatives.stream().map(a -> new Choice(a.id(), a.title(), a.status(),
                a.stops().stream().anyMatch(s -> !s.kind().equals("sight")) ? "sight-and-meal" : "sight-only"))
                .toList(), DecisionInputHash.sha256(input));
    }

    public static Answer evaluate(Capture capture, DecisionService service) {
        var builder = DecisionSpec.builder()
                .proposition("fully-sourced", q -> q.asking("Is any alternative marked provisional with no open questions? This is still not a confirmed booking."))
                .choice("next", q -> {
                    q.asking("Choose a non-invalid alternative that best fits the confirmed profile and sourced timing."
                            + " Choose research if material gaps prevent a useful tentative itinerary."
                            + " Never claim an unresolved alternative is confirmed.");
                    for (var alternative : capture.choices())
                        q.option(alternative.id(), alternative.title() + " (" + alternative.status() + ")");
                    q.option("research", "Ask or research before choosing an itinerary");
                })
                .rating("fit", q -> q.asking("Rate the best non-invalid alternative's fit and evidence")
                        .level("strong", "Provisionally fits with mapped evidence")
                        .level("possible", "Tentative with open questions")
                        .level("weak", "No usable alternative"))
                .build();
        long started = System.nanoTime();
        var answer = service.ask(DecisionRequest.of(capture.input(), builder));
        double decisionMs = (System.nanoTime() - started) / 1_000_000.0;
        var proposition = ((DecisionAnswer.Proposition) answer.answer("fully-sourced")).getOutcome();
        var choice = ((DecisionAnswer.Choice) answer.answer("next")).getOutcome();
        var rating = ((DecisionAnswer.Rating) answer.answer("fit")).getOutcome();
        String selected = BayesAgent.value(choice);
        String type = capture.choices().stream().filter(a -> a.id().equals(selected)).findFirst()
                .map(Choice::type).orElse("unknown");
        var shape = ClassificationSpec.builder().asking("Report the supplied selected itinerary type")
                .category("sight-and-meal", "The selected itinerary contains a sight and a meal or coffee")
                .category("sight-only", "The selected itinerary contains only a sight")
                .category("unknown", "No itinerary was selected")
                .build();
        started = System.nanoTime();
        var classification = service.classify(ClassificationRequest.of(
                capture.input() + "\nSelected itinerary type: " + type, shape));
        double classificationMs = (System.nanoTime() - started) / 1_000_000.0;
        String model = proposition instanceof PropositionResult.Answered a && a.getProvenance() != null
                ? a.getProvenance().getProvider() + "/" + a.getProvenance().getModelName() : "unreported";
        return new Answer(proposition instanceof PropositionResult.Answered a ? Boolean.toString(a.getAnswer())
                : proposition instanceof PropositionResult.Failure f ? "failure:" + f.getReason() : "inconclusive",
                selected, BayesAgent.ratingValue(rating), BayesAgent.value(classification), model,
                decisionMs, classificationMs);
    }
}
