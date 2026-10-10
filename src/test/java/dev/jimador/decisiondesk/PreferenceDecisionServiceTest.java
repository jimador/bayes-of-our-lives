package dev.jimador.decisiondesk;

import static org.junit.jupiter.api.Assertions.*;

import com.embabel.common.ai.classification.ClassificationRequest;
import com.embabel.common.ai.classification.ClassificationResult;
import com.embabel.common.ai.classification.ClassificationSpec;
import org.junit.jupiter.api.Test;

class PreferenceDecisionServiceTest {
    private final PreferenceDecisionService service = new PreferenceDecisionService();
    private final ClassificationSpec spec = ClassificationSpec.builder()
            .asking("What is today's kayaking preference?")
            .category("paddle", "Paddle today")
            .category("plan", "Plan another day")
            .category("rest", "Take a day off")
            .build();

    @Test void routesSyntheticPreferences() {
        for (var c : DeskRunner.CASES) {
            var result = service.classify(ClassificationRequest.of(c.note(), spec));
            assertInstanceOf(ClassificationResult.Selected.class, result);
            assertEquals(c.expected(), ((ClassificationResult.Selected) result).getCategoryId(), c.id());
        }
    }
}
