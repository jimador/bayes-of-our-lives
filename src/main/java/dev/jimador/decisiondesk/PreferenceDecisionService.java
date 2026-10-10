package dev.jimador.decisiondesk;

import com.embabel.common.ai.classification.ClassificationRequest;
import com.embabel.common.ai.classification.ClassificationResult;
import com.embabel.common.ai.classification.ModelProvenance;
import com.embabel.common.ai.decision.DecisionCapabilities;
import com.embabel.common.ai.decision.DecisionService;
import com.embabel.common.ai.decision.PropositionRequest;
import com.embabel.common.ai.decision.PropositionResult;
import com.embabel.common.ai.decision.QuestionKind;
import java.util.Locale;
import java.util.Set;

/** An inspectable example of an application-owned choice service, scoped to these synthetic notes. */
public final class PreferenceDecisionService implements DecisionService {
    private static final ModelProvenance PROVENANCE = new ModelProvenance("preference-rules-v1", "local");

    @Override public String getName() { return "preference-rules-v1"; }
    @Override public String getProvider() { return "local"; }
    @Override public DecisionCapabilities capabilities() { return DecisionCapabilities.of(Set.of(QuestionKind.CHOICE)); }

    @Override
    public ClassificationResult classify(ClassificationRequest request) {
        String text = request.getInput().toLowerCase(Locale.ROOT);
        String category = text.matches("(?s).*(rest|day off|no kayak|nothing kayak).*") ? "rest"
                : text.matches("(?s).*(plan|research|map|route|another day|next weekend).*") ? "plan"
                : "paddle";
        return request.getSpec().selected(category, PROVENANCE);
    }

    @Override
    public PropositionResult assess(PropositionRequest request) {
        return new PropositionResult.Failure(com.embabel.common.ai.classification.FailureReason.UNAVAILABLE);
    }
}
