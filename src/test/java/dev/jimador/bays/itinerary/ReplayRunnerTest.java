package dev.jimador.bays.itinerary;

import com.embabel.common.ai.decision.DecisionRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jimador.bays.decision.LocalItineraryDecisions;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

class ReplayRunnerTest {
    private static DecisionReplay.Capture capture() {
        return new DecisionReplay.Capture("Alternative a status: unresolved\nAlternative a open questions: 2\n"
                + "Alternative a liked food stops: 1\nAlternative a disliked food stops: 0\n"
                + "Alternative a supported breakfast stops: 1\nAlternative a uncertain breakfast stops: 0\n"
                + "Alternative b status: invalid\nAlternative b open questions: 3\n"
                + "Alternative b liked food stops: 0\nAlternative b disliked food stops: 1\n"
                + "Alternative b supported breakfast stops: 0\nAlternative b uncertain breakfast stops: 1",
                List.of(new DecisionReplay.Choice("a", "Art and seafood", "unresolved", "sight-and-meal"),
                        new DecisionReplay.Choice("b", "Closed place", "invalid", "sight-and-meal")),
                dev.jimador.bays.decision.DecisionInputHash.sha256("Alternative a status: unresolved\nAlternative a open questions: 2\n"
                + "Alternative a liked food stops: 1\nAlternative a disliked food stops: 0\n"
                + "Alternative a supported breakfast stops: 1\nAlternative a uncertain breakfast stops: 0\n"
                + "Alternative b status: invalid\nAlternative b open questions: 3\n"
                + "Alternative b liked food stops: 0\nAlternative b disliked food stops: 1\n"
                + "Alternative b supported breakfast stops: 0\nAlternative b uncertain breakfast stops: 1"));
    }
    @Test void captureRoundTripsAndRejectsChangedInput() throws Exception {
        var mapper = new ObjectMapper();
        var original = capture();
        assertEquals(original, mapper.readValue(mapper.writeValueAsBytes(original), DecisionReplay.Capture.class));
        assertThrows(IllegalArgumentException.class, () -> new DecisionReplay.Capture("altered", original.choices(), original.inputSha256()));
    }
    @Test void replayKeepsExactInputAcrossEnginesAndRepetitionsAndFormatsOutputs() {
        var local = spy(new LocalItineraryDecisions());
        var jev = spy(new LocalItineraryDecisions());
        var openai = spy(new LocalItineraryDecisions());
        var report = ReplayRunner.execute(capture(), 2, engine -> true,
                engine -> switch (engine) { case "jev" -> jev; case "openai" -> openai; default -> local; });
        assertEquals(6, report.rows().size());
        assertTrue(report.rows().stream().allMatch(r -> r.status().equals("ok") && r.choice().equals("a")));
        for (var service : List.of(local, jev, openai)) {
            ArgumentCaptor<DecisionRequest> sent = ArgumentCaptor.forClass(DecisionRequest.class);
            verify(service, times(2)).ask(sent.capture());
            assertTrue(sent.getAllValues().stream().allMatch(v -> v.getInput().equals(capture().input())));
        }
        String markdown = ReplayRunner.markdown(report);
        assertTrue(markdown.contains("| Engine | Run | Status | Fully sourced | Next | Fit | Classification | Decision ms | Classification ms | Total ms | Note |"));
        assertTrue(markdown.contains(report.inputSha256()));
        assertFalse(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(report).toString().contains(capture().input()));
    }
    @Test void rejectsPrivateDietaryNotesBeforeWritingCapture() {
        var request = new ItineraryRequest("Fort Lauderdale", "Downtown", java.time.LocalDate.of(2026, 10, 20),
                java.time.LocalTime.of(7, 0), java.time.LocalTime.of(14, 0), true, false, false,
                true, false, 2, 0, "art", "steady", false, "private allergy marker", "local", true, true);
        var store = new ReplayCaptureStore(".bayes-data/private-test-capture.json");
        assertDoesNotThrow(() -> store.save(capture(), request));
        assertFalse(java.nio.file.Files.exists(java.nio.file.Path.of(".bayes-data/private-test-capture.json")));
    }

    @Test void localRatingPenalizesDislikedCuisineEvenWhenAnAlternativeIsChosen() {
        var local = new LocalItineraryDecisions();
        String input = "Alternative a status: provisional\nAlternative a open questions: 0\n"
                + "Alternative a disliked food stops: 1\nAlternative a liked food stops: 0\n"
                + "Alternative b status: invalid\nAlternative b open questions: 5";
        var result = local.rate(input, mock(com.embabel.common.ai.decision.RatingQuestionSpec.class));
        assertEquals("weak", BayesAgent.ratingValue(result));
    }

    @Test void recordsMissingKeysAndFailuresWithoutUnboundedRetries() {
        var skipped = ReplayRunner.execute(capture(), 2, engine -> engine.equals("local"),
                engine -> new LocalItineraryDecisions());
        assertEquals(2, skipped.rows().stream().filter(r -> r.status().equals("skipped")).count());
        assertEquals(2, skipped.rows().stream().filter(r -> r.status().equals("ok")).count());
        var failures = ReplayRunner.execute(capture(), 2, engine -> true,
                engine -> { if (engine.equals("jev")) throw new IllegalStateException("private detail");
                    return new LocalItineraryDecisions(); });
        assertEquals(2, failures.rows().stream().filter(r -> r.status().equals("failed")).count());
        assertFalse(ReplayRunner.markdown(failures).contains("private detail"));
        assertThrows(IllegalArgumentException.class, () -> ReplayRunner.execute(capture(), 11,
                engine -> true, engine -> new LocalItineraryDecisions()));
    }
}
