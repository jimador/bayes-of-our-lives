package dev.jimador.bays;

import com.embabel.common.ai.classification.ClassificationRequest;
import com.embabel.common.ai.classification.ClassificationResult;
import com.embabel.common.ai.classification.ModelProvenance;
import com.embabel.common.ai.decision.*;
import com.embabel.common.ai.decision.spi.RatingAssessment;
import java.util.EnumSet;
import java.util.List;

/** Offline control for the same typed itinerary questions used by Jev and GPT-4.1. */
public final class LocalItineraryDecisions implements DecisionService, RatingAssessment {
    private static final ModelProvenance PROVENANCE = new ModelProvenance("bayes-itinerary-rules-v1", "local");
    @Override public String getName() { return "bayes-itinerary-rules-v1"; }
    @Override public String getProvider() { return "local"; }
    @Override public DecisionCapabilities capabilities() {
        return DecisionCapabilities.of(EnumSet.allOf(QuestionKind.class));
    }
    @Override public PropositionResult assess(PropositionRequest request) {
        return new PropositionResult.Answered(request.getInput().contains("status: provisional"), PROVENANCE);
    }
    @Override public ClassificationResult classify(ClassificationRequest request) {
        List<String> ids = request.getSpec().getCategories().stream().map(c -> c.getId()).toList();
        if (ids.contains("research")) return request.getSpec().selected(choice(request.getInput(), ids), PROVENANCE);
        String type = field(request.getInput(), "Selected itinerary type");
        return request.getSpec().selected(ids.contains(type) ? type : "unknown", PROVENANCE);
    }
    @Override public RatingResult rate(String input, RatingQuestionSpec question) {
        String choice = choice(input, List.of("a", "b", "research"));
        String status = field(input, "Alternative " + choice + " status");
        return new RatingResult.Answered(PROVENANCE, switch (status) {
            case "provisional", "unresolved" -> "possible"; default -> "weak";
        });
    }
    private static String choice(String input, List<String> ids) {
        String best = "research";
        int score = -999;
        for (String id : List.of("a", "b")) {
            if (!ids.contains(id)) continue;
            String status = field(input, "Alternative " + id + " status");
            int value = status.equals("provisional") ? 100 : status.equals("unresolved") ? 50 : -100;
            int gaps = number(field(input, "Alternative " + id + " open questions"));
            value -= gaps;
            if (value > score && value >= 0) { best = id; score = value; }
        }
        return best;
    }
    private static int number(String value) {
        try { return Integer.parseInt(value); } catch (NumberFormatException failure) { return 99; }
    }
    private static String field(String input, String key) {
        for (String line : input.split("\\R")) if (line.startsWith(key + ": "))
            return line.substring(key.length() + 2).trim();
        return "";
    }
}
