package dev.jimador.bayes.itinerary;

import com.embabel.common.ai.model.LlmOptions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jimador.bayes.decision.LocalItineraryDecisions;
import dev.jimador.bayes.provider.DayGuideTools;
import dev.jimador.bayes.provider.PageEvidence;
import dev.jimador.bayes.provider.Place;
import dev.jimador.bayes.provider.Places;
import dev.jimador.bayes.provider.PublicPages;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Recorded public fixtures only: no provider, model, page retrieval or route request. */
class CapturedItineraryValidationTest {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private record Fixture(JsonNode captured, ItineraryContext context, ResearchFindings research,
                           Itinerary.Draft draft, Places places, PublicPages pages) {}

    @Test void recordedLocalAndJevPlansKeepTheirEvidenceAndUseTheCorrectedValidatorOffline() throws Exception {
        List<Map<String, Object>> assessments = new ArrayList<>();
        for (String engine : List.of("local", "jev")) {
            Path input = Path.of("results/live-fort-lauderdale-2026-10-14-final-two-" + engine + ".json");
            Fixture f = fixture(JSON.readTree(input.toFile()));
            var checked = ItineraryValidation.plans(f.draft(), f.research(), f.context());
            var recordedAnswer = JSON.treeToValue(f.captured().path("decision"), ItineraryDecisions.Answer.class);
            if (engine.equals("local")) {
                for (var original : f.captured().path("alternatives")) {
                    assertEquals("unresolved", original.path("status").asText());
                    assertEquals(List.of("Provide distinct alternatives with different venues or stop order; shifting times alone is not another choice."),
                            JSON.convertValue(original.path("openQuestions"), JSON.getTypeFactory().constructCollectionType(List.class, String.class)));
                }
                assertEquals(1, checked.alternatives().size());
                assertEquals("a", checked.alternatives().getFirst().id());
            } else {
                assertEquals(List.of("a", "b"), checked.alternatives().stream().map(Itinerary.Alternative::id).toList());
                assertEquals("false", recordedAnswer.coverage());
                assertEquals("b", recordedAnswer.choice());
                assertEquals("possible", recordedAnswer.rating());
                assertEquals("usable-outline", recordedAnswer.classification());
            }
            for (var alternative : checked.alternatives()) {
                assertEquals("usable", alternative.status());
                assertTrue(alternative.openQuestions().isEmpty());
                JsonNode original = null;
                for (var candidate : f.captured().path("alternatives"))
                    if (candidate.path("id").asText().equals(alternative.id())) original = candidate;
                assertNotNull(original);
                var originalAlternative = JSON.treeToValue(original, Itinerary.Alternative.class);
                assertEquals(originalAlternative.stops(), alternative.stops());
                assertEquals(originalAlternative.reason(), alternative.reason());
                assertEquals(originalAlternative.title(), alternative.title());
            }
            var localAnswer = engine.equals("local") ? ItineraryDecisions.evaluate(
                    DecisionBrief.from(f.context().brief(), checked.alternatives()), checked.alternatives(),
                    new LocalItineraryDecisions(JSON), new LocalItineraryDecisions(JSON), JSON) : null;
            if (localAnswer != null) {
                assertEquals("true", localAnswer.coverage());
                assertEquals("a", localAnswer.choice());
                assertEquals("strong", localAnswer.rating());
                assertEquals("usable-outline", localAnswer.classification());
            }
            var answer = localAnswer == null ? recordedAnswer : localAnswer;
            var agent = new BayesAgent(mock(ItineraryMemories.class), f.places(), f.pages(), JSON,
                    LlmOptions.withModel("fixture-not-called"), LlmOptions.withModel("fixture-not-called"));
            var completed = agent.completeItinerary(f.context(), f.research(), checked, answer);
            assertEquals("usable", completed.status());
            assertEquals(answer.choice(), completed.selectedAlternative());
            assertSame(answer, completed.review());
            verify(f.places(), never()).locate(anyString(), anyString());
            assessments.add(Map.of(
                    "engine", engine, "input", input.toString(), "inputSha256", sha256(input),
                    "recordedStatus", f.captured().path("status").asText(),
                    "recordedDecision", f.captured().path("decision"),
                    "offlineAlternatives", checked.alternatives().stream().map(a -> Map.of(
                            "id", a.id(), "status", a.status(), "materialQuestions", a.openQuestions(),
                            "stopIds", a.stops().stream().map(Itinerary.Stop::placeId).toList(),
                            "originalTimesAndEvidencePreserved", true)).toList(),
                    "offlineSelectedStatus", completed.status(), "offlineSelectedId", completed.selectedAlternative(),
                    "decisionAssessment", localAnswer == null
                            ? Map.of("mode", "recorded-Jev-answer-preserved-no-ask-or-classify", "coverage", answer.coverage(),
                                "choice", answer.choice(), "rating", answer.rating(), "classification", answer.classification(), "model", answer.model())
                            : Map.of("mode", "deterministic-local-rules-rerun-offline", "coverage", answer.coverage(),
                                "choice", answer.choice(), "rating", answer.rating(), "classification", answer.classification(), "model", answer.model())));
        }
        String output = System.getProperty("bayes.capture.assessment-output");
        if (output != null) {
            var assessment = Map.of("schema", 1,
                    "recordedSourceCommit", "633590ab1096321c7b284813516d51e60ee0c024",
                    "captureCommit", "c2921e28de4889bf50f1c1319d7ff8e0218b5dd1",
                    "validatorSourceSha256", sha256(Path.of("src/main/java/dev/jimador/bayes/itinerary/ItineraryValidation.java")),
                    "assessmentMode", "offline-recorded-fixture-validation",
                    "reran", List.of("research source normalization", "individual plan invariants", "stable duplicate retention",
                            "local Java decisions only", "completeItinerary selected-status mapping"),
                    "notRerun", List.of("public map/page/route services", "OpenAI research/planning/decisions", "Jev ask/classify", "browser outings"),
                    "fixtureLimits", List.of("Coordinates are fixture labels only, absent from public captures.",
                            "Per-leg drives come from captured baseTravelMinutes; uncaptured routes remain unknown.",
                            "Page IDs/URLs/statuses are captured; omitted excerpt content is not reconstructed.",
                            "Research windows and meal support are recorded interpretations, not newly verified venue facts.",
                            "Recorded timings and provider answers are unchanged; no new remote latency or coverage rationale is inferred."),
                    "assessments", assessments);
            JSON.writerWithDefaultPrettyPrinter().writeValue(Path.of(output).toFile(), assessment);
        }
    }

    private static Fixture fixture(JsonNode captured) throws Exception {
        Places places = mock(Places.class);
        PublicPages pages = mock(PublicPages.class);
        var location = new Places.Location(captured.path("city").asText(), captured.path("destinationTimezone").asText(), 0, 0, 0, 0);
        var tools = new DayGuideTools(places, pages, location, List.of("seafood", "cuban"));
        Map<String, List<Places.MappedPlace>> searches = new LinkedHashMap<>();
        Map<String, Double> coordinate = new LinkedHashMap<>();
        Map<Double, String> identity = new LinkedHashMap<>();
        identity.put(0.0, "origin");
        List<ResearchFindings.PlaceFinding> facts = new ArrayList<>();
        for (var view : captured.path("researchedPlaces")) {
            Place place = JSON.treeToValue(view.path("place"), Place.class);
            double label = coordinate.size() + 1;
            coordinate.put(place.id(), label);
            identity.put(label, place.id());
            String cuisine = place.category().equals("restaurant") ? place.tags().getOrDefault("cuisine", "") : "";
            searches.computeIfAbsent(place.category() + ":" + cuisine, k -> new ArrayList<>())
                    .add(new Places.MappedPlace(place, label, label));
            List<PageEvidence> recordedPages = new ArrayList<>();
            for (var page : view.path("pages")) {
                assertFalse(page.has("excerpt"));
                recordedPages.add(new PageEvidence(page.path("id").asText(), page.path("placeId").asText(),
                        page.path("url").asText(), page.path("status").asText(), null));
            }
            when(pages.read(place, location.city())).thenReturn(List.copyOf(recordedPages));
            facts.add(JSON.treeToValue(view.path("interpretation"), ResearchFindings.PlaceFinding.class));
        }
        for (var entry : searches.entrySet()) {
            String[] query = entry.getKey().split(":", -1);
            when(places.search(location, query[0], query[1])).thenReturn(entry.getValue());
            tools.search(query[0], query[1]);
        }
        for (String id : coordinate.keySet()) tools.readPlace(id);
        Map<String, Integer> drives = new LinkedHashMap<>();
        List<Itinerary.AlternativeDraft> alternatives = new ArrayList<>();
        for (var alternative : captured.path("alternatives")) {
            String from = "origin";
            List<Itinerary.StopDraft> stops = new ArrayList<>();
            for (var stop : alternative.path("stops")) {
                String to = stop.path("placeId").asText();
                Integer drive = stop.path("baseTravelMinutes").isNull() ? null : stop.path("baseTravelMinutes").asInt();
                String leg = from + ":" + to;
                if (drives.containsKey(leg)) assertEquals(drives.get(leg), drive);
                drives.put(leg, drive);
                stops.add(new Itinerary.StopDraft(stop.path("kind").asText(), to,
                        LocalTime.parse(stop.path("startsAt").asText()), LocalTime.parse(stop.path("endsAt").asText())));
                from = to;
            }
            alternatives.add(new Itinerary.AlternativeDraft(alternative.path("id").asText(), List.copyOf(stops),
                    alternative.path("reason").asText(), JSON.convertValue(alternative.path("notes"),
                    JSON.getTypeFactory().constructCollectionType(List.class, String.class))));
        }
        when(places.driveMinutes(anyDouble(), anyDouble(), anyDouble(), anyDouble())).thenAnswer(call ->
                drives.get(identity.get((Double) call.getArgument(0)) + ":" + identity.get((Double) call.getArgument(2))));
        var brief = new ItineraryBrief(location.city(), LocalDate.parse(captured.path("date").asText()),
                LocalTime.of(7, 0), LocalTime.of(14, 0), 2, 0, "art", "steady", false,
                List.of("breakfast", "lunch", "sight"), List.of("seafood", "cuban"), List.of("italian"));
        var context = new ItineraryContext(brief, new ItineraryMemories.Accepted("offline-recorded-fixture", "art", "steady", List.of()),
                tools, location.timezone(), false, "local");
        var research = new ResearchFindings(List.copyOf(facts));
        assertEquals(research, ItineraryValidation.research(research, List.copyOf(coordinate.keySet()), context));
        return new Fixture(captured, context, research, new Itinerary.Draft(List.copyOf(alternatives)), places, pages);
    }
    private static String sha256(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }
}
