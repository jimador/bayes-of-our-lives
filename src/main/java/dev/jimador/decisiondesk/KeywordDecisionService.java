package dev.jimador.decisiondesk;

import com.embabel.common.ai.classification.ClassificationRequest;
import com.embabel.common.ai.classification.ClassificationResult;
import com.embabel.common.ai.classification.ModelProvenance;
import com.embabel.common.ai.decision.DecisionCapabilities;
import com.embabel.common.ai.decision.DecisionService;
import com.embabel.common.ai.decision.PropositionRequest;
import com.embabel.common.ai.decision.PropositionResult;
import com.embabel.common.ai.decision.QuestionKind;
import java.util.Set;

/** A deliberately simple, inspectable stand-in for any application-owned decision system. */
public final class KeywordDecisionService implements DecisionService {
    private static final ModelProvenance PROVENANCE = new ModelProvenance("keywords-v1", "local");

    @Override public String getName() { return "keywords-v1"; }
    @Override public String getProvider() { return "local"; }
    @Override public DecisionCapabilities capabilities() { return DecisionCapabilities.of(Set.of(QuestionKind.CHOICE)); }

    @Override
    public ClassificationResult classify(ClassificationRequest request) {
        String text = request.getInput().toLowerCase(java.util.Locale.ROOT);
        String category = text.matches("(?s).*(exit|fire|smoke|trip|injur|blocked door|electrical).*") ? "safety"
                : text.matches("(?s).*(speaker|microphone|feedback|audio|monitor|sound).*") ? "sound"
                : "props";
        return request.getSpec().selected(category, PROVENANCE);
    }

    @Override
    public PropositionResult assess(PropositionRequest request) {
        return new PropositionResult.Failure(com.embabel.common.ai.classification.FailureReason.UNAVAILABLE);
    }
}
