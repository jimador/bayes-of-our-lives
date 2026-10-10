package dev.jimador.decisiondesk;

import static org.junit.jupiter.api.Assertions.*;

import com.embabel.common.ai.classification.ClassificationRequest;
import com.embabel.common.ai.classification.ClassificationResult;
import com.embabel.common.ai.classification.ClassificationSpec;
import org.junit.jupiter.api.Test;

class KeywordDecisionServiceTest {
    private final KeywordDecisionService service = new KeywordDecisionService();
    private final ClassificationSpec spec = ClassificationSpec.builder()
            .asking("Which crew?")
            .category("props", "Props")
            .category("sound", "Sound")
            .category("safety", "Safety")
            .build();

    @Test void routesAllSyntheticCasesToKnownCrew() {
        for (var c : DeskRunner.CASES) {
            var result = service.classify(ClassificationRequest.of(c.note(), spec));
            assertInstanceOf(ClassificationResult.Selected.class, result);
            assertEquals(c.expected(), ((ClassificationResult.Selected) result).getCategoryId(), c.id());
        }
    }
}
