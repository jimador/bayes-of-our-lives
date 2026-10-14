package dev.jimador.bayes.decision;

import com.embabel.common.ai.classification.ClassificationRequest;
import com.embabel.common.ai.classification.ClassificationResult;
import com.embabel.common.ai.classification.ModelProvenance;
import com.embabel.common.ai.decision.DecisionCapabilities;
import com.embabel.common.ai.decision.DecisionService;
import com.embabel.common.ai.decision.PropositionRequest;
import com.embabel.common.ai.decision.PropositionResult;
import com.embabel.common.ai.decision.QuestionKind;
import com.embabel.common.ai.decision.RatingQuestionSpec;
import com.embabel.common.ai.decision.RatingResult;
import com.embabel.common.ai.decision.spi.RatingAssessment;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jimador.bayes.itinerary.DecisionBrief;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

/** Local control reads the same JSON DTO used by configured remote decision services. */
public final class LocalItineraryDecisions implements DecisionService, RatingAssessment {
    private static final ModelProvenance PROVENANCE = new ModelProvenance("bayes-itinerary-rules-v2", "local");
    private final ObjectMapper mapper;
    public LocalItineraryDecisions(ObjectMapper mapper) { this.mapper = mapper; }
    @Override public String getName() { return "bayes-itinerary-rules-v2"; }
    @Override public String getProvider() { return "local"; }
    @Override public DecisionCapabilities capabilities() { return DecisionCapabilities.of(EnumSet.allOf(QuestionKind.class)); }
    @Override public PropositionResult assess(PropositionRequest request) {
        return new PropositionResult.Answered(read(request.getInput()).readiness().equals("usable-outline"), PROVENANCE);
    }
    @Override public ClassificationResult classify(ClassificationRequest request) {
        DecisionBrief brief = read(request.getInput());
        List<String> ids = request.getSpec().getCategories().stream().map(c -> c.getId()).toList();
        String selected = ids.contains("research") ? best(brief, ids) : brief.readiness();
        return request.getSpec().selected(selected, PROVENANCE);
    }
    @Override public RatingResult rate(String input, RatingQuestionSpec question) {
        DecisionBrief brief = read(input);
        String selected = best(brief, brief.alternatives().stream().map(DecisionBrief.Option::id).toList());
        var best = brief.alternatives().stream().filter(a -> a.id().equals(selected)).findFirst().orElse(null);
        return new RatingResult.Answered(PROVENANCE, best == null ? "weak"
                : best.status().equals("usable") && best.dislikedMeals() == 0 ? "strong" : "possible");
    }
    private static String best(DecisionBrief brief, List<String> allowed) {
        return brief.alternatives().stream().filter(a -> allowed.contains(a.id()) && !a.status().equals("invalid"))
                .max(Comparator.comparingInt((DecisionBrief.Option a) -> (a.status().equals("usable") ? 1000 : 0)
                        + 20 * a.likedMeals() - 80 * a.dislikedMeals() - a.materialQuestions())
                        .thenComparing(DecisionBrief.Option::id, Comparator.reverseOrder()))
                .map(DecisionBrief.Option::id).orElse("research");
    }
    private DecisionBrief read(String input) {
        try { return mapper.readValue(input, DecisionBrief.class); }
        catch (JsonProcessingException failure) { throw new IllegalArgumentException("Invalid typed decision brief"); }
    }
}
