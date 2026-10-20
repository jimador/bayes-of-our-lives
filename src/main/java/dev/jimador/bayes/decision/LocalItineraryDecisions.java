package dev.jimador.bayes.decision;

import com.embabel.common.ai.classification.Category;
import com.embabel.common.ai.classification.ClassificationRequest;
import com.embabel.common.ai.classification.ClassificationResult;
import com.embabel.common.ai.classification.FailureReason;
import com.embabel.common.ai.classification.ModelProvenance;
import com.embabel.common.ai.decision.ChoiceQuestionSpec;
import com.embabel.common.ai.decision.DecisionCapabilities;
import com.embabel.common.ai.decision.DecisionRequest;
import com.embabel.common.ai.decision.DecisionResponse;
import com.embabel.common.ai.decision.DecisionService;
import com.embabel.common.ai.decision.PropositionQuestionSpec;
import com.embabel.common.ai.decision.PropositionRequest;
import com.embabel.common.ai.decision.PropositionResult;
import com.embabel.common.ai.decision.QuestionKind;
import com.embabel.common.ai.decision.RatingQuestionSpec;
import com.embabel.common.ai.decision.RatingResult;
import com.embabel.common.ai.decision.spi.QuestionSetExecution;
import dev.jimador.bayes.itinerary.DecisionBrief;
import dev.jimador.bayes.itinerary.DecisionBriefs;
import dev.jimador.bayes.itinerary.ItineraryDecisions;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/**
 * Deterministic Java rules that answer the same itinerary questions as Jev and OpenAI.
 *
 * <p>It is registered like any other decision service and selected by the {@code local} role.
 * Rather than parse the rendered input, it looks up the typed brief that input was rendered from.
 */
public final class LocalItineraryDecisions implements DecisionService, QuestionSetExecution {

    private static final ModelProvenance PROVENANCE =
            new ModelProvenance("bayes-itinerary-rules-v2", "local");

    private final DecisionBriefs briefs;

    public LocalItineraryDecisions(DecisionBriefs briefs) {
        this.briefs = briefs;
    }

    @Override
    public String getName() {
        return "bayes-itinerary-rules-v2";
    }

    @Override
    public String getProvider() {
        return "local";
    }

    @Override
    public DecisionCapabilities capabilities() {
        return DecisionCapabilities.of(EnumSet.allOf(QuestionKind.class));
    }

    @Override
    public DecisionResponse askQuestionSet(DecisionRequest request) {
        var brief = briefs.lookup(request.getInput()).orElse(null);
        if (brief == null)
            return DecisionResponse.failed(request.getSpec(), FailureReason.UNAVAILABLE);
        var response = DecisionResponse.builder(request.getSpec());
        for (var question : request.getSpec().getQuestions()) {
            switch (question) {
                case PropositionQuestionSpec coverage -> response.answer(coverage, covered(brief));
                case ChoiceQuestionSpec choice ->
                        response.answer(
                                choice, choose(brief, choice.getName(), choice.getOptions()));
                case RatingQuestionSpec confidence ->
                        response.answer(confidence, confidence(brief));
                default ->
                        throw new IllegalStateException(
                                "Unsupported question kind " + question.getKind());
            }
        }
        return response.build();
    }

    @Override
    public ClassificationResult classify(ClassificationRequest request) {
        var spec = request.getSpec();
        return briefs.lookup(request.getInput())
                .map(brief -> choose(brief, spec.getQuestion().getName(), spec.getCategories()))
                .orElseGet(() -> new ClassificationResult.Failure(FailureReason.UNAVAILABLE));
    }

    @Override
    public PropositionResult assess(PropositionRequest request) {
        return briefs.lookup(request.getInput())
                .map(LocalItineraryDecisions::covered)
                .orElseGet(() -> new PropositionResult.Failure(FailureReason.UNAVAILABLE));
    }

    private static PropositionResult covered(DecisionBrief brief) {
        return new PropositionResult.Answered(
                brief.readiness().equals("usable-outline"), PROVENANCE);
    }

    private static ClassificationResult choose(
            DecisionBrief brief, String question, List<Category> options) {
        if (question.equals(ItineraryDecisions.READINESS.getQuestion().getName())) {
            return new ClassificationResult.Selected(brief.readiness(), PROVENANCE);
        }
        var offered = options.stream().map(Category::getId).toList();
        return new ClassificationResult.Selected(best(brief, offered), PROVENANCE);
    }

    private static RatingResult confidence(DecisionBrief brief) {
        String chosen =
                best(brief, brief.alternatives().stream().map(DecisionBrief.Option::id).toList());
        String level =
                brief.alternatives().stream()
                        .filter(a -> a.id().equals(chosen))
                        .findFirst()
                        .map(
                                a ->
                                        a.status().equals("usable") && a.dislikedMeals() == 0
                                                ? "strong"
                                                : "possible")
                        .orElse("weak");
        return new RatingResult.Answered(PROVENANCE, level);
    }

    // Usable beats unresolved; then liked cuisines up, disliked cuisines and open questions down.
    private static String best(DecisionBrief brief, List<String> offered) {
        Comparator<DecisionBrief.Option> fit =
                Comparator.comparingInt(
                        a ->
                                (a.status().equals("usable") ? 1000 : 0)
                                        + 20 * a.likedMeals()
                                        - 80 * a.dislikedMeals()
                                        - a.materialQuestions());
        return brief.alternatives().stream()
                .filter(a -> offered.contains(a.id()) && !a.status().equals("invalid"))
                .max(fit.thenComparing(DecisionBrief.Option::id, Comparator.reverseOrder()))
                .map(DecisionBrief.Option::id)
                .orElse("research");
    }
}
