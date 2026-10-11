package dev.jimador.decisiondesk;

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
import java.util.EnumSet;

/** An application-owned implementation of the same illustrative choice contract. */
public final class PreferenceDecisionService implements DecisionService, RatingAssessment {
    private static final ModelProvenance PROVENANCE = new ModelProvenance("coastal-rules-v1", "local");

    @Override public String getName() { return "coastal-rules-v1"; }
    @Override public String getProvider() { return "local"; }
    @Override public DecisionCapabilities capabilities() { return DecisionCapabilities.of(EnumSet.allOf(QuestionKind.class)); }

    @Override
    public ClassificationResult classify(ClassificationRequest request) {
        if (request.getSpec().getCategories().stream().anyMatch(c -> c.getId().equals("candidate"))
                && !field(request.getInput(), "Outing policy").isBlank())
            return request.getSpec().selected(policy(request.getInput()), PROVENANCE);
        if (request.getSpec().getCategories().stream().anyMatch(c -> c.getId().equals("nearby"))) {
            String category = field(request.getInput(), "Outing category");
            if (!category.equals("nearby") && !category.equals("long-drive"))
                return new ClassificationResult.Failure(com.embabel.common.ai.classification.FailureReason.INVALID_RESPONSE);
            return request.getSpec().selected(category, PROVENANCE);
        }
        if (request.getSpec().getCategories().stream().anyMatch(c -> c.getId().equals("intracoastal"))) {
            String setting = field(request.getInput(), "Demo route setting");
            if (!setting.equals("intracoastal") && !setting.equals("ocean"))
                return new ClassificationResult.Failure(com.embabel.common.ai.classification.FailureReason.INVALID_RESPONSE);
            return request.getSpec().selected(setting, PROVENANCE);
        }
        return request.getSpec().selected(CoastalPolicy.evaluate(request.getInput()), PROVENANCE);
    }

    @Override
    public PropositionResult assess(PropositionRequest request) {
        String policy = policy(request.getInput());
        return new PropositionResult.Answered(!policy.equals("review"), PROVENANCE);
    }

    @Override
    public RatingResult rate(String input, RatingQuestionSpec question) {
        String choice = policy(input);
        String level = switch (choice) {
            case "candidate" -> "easy";
            case "defer" -> "hard";
            default -> "unknown";
        };
        return new RatingResult.Answered(PROVENANCE, level);
    }

    private static String field(String input, String name) {
        for (String line : input.split("\\R")) {
            if (line.startsWith(name + ": ")) return line.substring(name.length() + 2).trim();
        }
        return "";
    }
    private static String policy(String input) {
        String outing = field(input, "Outing policy");
        return outing.isBlank() ? CoastalPolicy.evaluate(input) : outing;
    }
}
