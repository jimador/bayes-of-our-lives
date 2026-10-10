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

/** An application-owned implementation of the same illustrative choice contract. */
public final class PreferenceDecisionService implements DecisionService {
    private static final ModelProvenance PROVENANCE = new ModelProvenance("coastal-rules-v1", "local");

    @Override public String getName() { return "coastal-rules-v1"; }
    @Override public String getProvider() { return "local"; }
    @Override public DecisionCapabilities capabilities() { return DecisionCapabilities.of(Set.of(QuestionKind.CHOICE)); }

    @Override
    public ClassificationResult classify(ClassificationRequest request) {
        return request.getSpec().selected(CoastalPolicy.evaluate(request.getInput()), PROVENANCE);
    }

    @Override
    public PropositionResult assess(PropositionRequest request) {
        return new PropositionResult.Failure(com.embabel.common.ai.classification.FailureReason.UNAVAILABLE);
    }
}
