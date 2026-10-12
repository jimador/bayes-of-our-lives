package dev.jimador.bays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.embabel.agent.core.AgentPlatform;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TripperConsentTest {
    @TempDir Path temporary;
    @Test void noRemoteConsentReturnsQuestionBeforeAgentOrProviderRuns() {
        var platform = mock(AgentPlatform.class);
        var decisions = mock(BayesDecisionServices.class);
        var service = new BayesItineraryService(platform, decisions,
                new ItineraryMemory(temporary.resolve("preferences.json")));
        var request = new ItineraryRequest("Miami", "Downtown", LocalDate.now().plusDays(1),
                LocalTime.of(10, 0), LocalTime.of(16, 0), false, false, false,
                true, false, 2, 0, "art", "steady", false, "", "local", true, false);
        var result = service.plan(request);
        assertEquals("needs_input", result.status());
        assertTrue(result.questions().getFirst().contains("OpenAI"));
        verifyNoInteractions(platform, decisions);
    }
}
