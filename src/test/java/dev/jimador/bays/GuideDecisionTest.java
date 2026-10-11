package dev.jimador.bays;

import com.embabel.common.ai.decision.DecisionAnswer;
import com.embabel.common.ai.decision.DecisionRequest;
import com.embabel.common.ai.decision.DecisionSpec;
import com.embabel.common.ai.decision.PropositionResult;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GuideDecisionTest {
    private static final DecisionSpec FIT = DecisionSpec.builder()
            .proposition("interest-match", q -> q.asking("Exact tag match?"))
            .choice("fit", q -> q.asking("Fit?")
                    .option("prefer", "all match").option("consider", "one matches").option("pass", "none match"))
            .rating("strength", q -> q.asking("Strength?")
                    .level("strong", "prefer").level("possible", "consider").level("weak", "pass"))
            .build();

    @Test void fixedFactsStayEligibleWhileMemoryChangesTypedAnswers() {
        var option = GuidePlaces.fixed("sights").options().getFirst();
        var art = new GuideMemories.Snapshot("art-quiet", "art", "bakery", "quiet", 15, "test", List.of());
        var science = new GuideMemories.Snapshot("science-lively", "science", "spicy", "lively", 30, "test", List.of());
        assertEquals("eligible", BaysAgent.hardStatus(option, 90));
        var service = new LocalGuideDecisions();
        String artInput = BaysAgent.decisionInput(option, art, "sights");
        String scienceInput = BaysAgent.decisionInput(option, science, "sights");
        var artAnswers = service.ask(DecisionRequest.of(artInput, FIT));
        var scienceAnswers = service.ask(DecisionRequest.of(scienceInput, FIT));
        assertTrue(((PropositionResult.Answered) ((DecisionAnswer.Proposition) artAnswers.answer("interest-match")).getOutcome()).getAnswer());
        assertFalse(((PropositionResult.Answered) ((DecisionAnswer.Proposition) scienceAnswers.answer("interest-match")).getOutcome()).getAnswer());
        assertEquals("prefer", BaysAgent.value(((DecisionAnswer.Choice) artAnswers.answer("fit")).getOutcome()));
        assertEquals("consider", BaysAgent.value(((DecisionAnswer.Choice) scienceAnswers.answer("fit")).getOutcome()));
        assertEquals("strong", BaysAgent.ratingValue(((DecisionAnswer.Rating) artAnswers.answer("strength")).getOutcome()));
        assertEquals("possible", BaysAgent.ratingValue(((DecisionAnswer.Rating) scienceAnswers.answer("strength")).getOutcome()));
        assertTrue(artInput.contains("Traveler interest: art"));
        assertTrue(scienceInput.contains("Traveler interest: science"));
    }

    @Test void timeAndMissingRouteAreHardConstraintsIndependentOfMemory() {
        var science = GuidePlaces.fixed("sights").options().get(1);
        assertEquals("defer", BaysAgent.hardStatus(science, 60));
        var unknownRoute = new GuidePlaces.Option("Unknown", "https://example.org", "unknown",
                "unknown", "unknown", null, 45);
        assertEquals("review", BaysAgent.hardStatus(unknownRoute, 90));
    }

    @Test void mealIntentUsesMealMemoryWithoutClaimingDietarySafety() {
        var bakery = GuidePlaces.fixed("meal").options().getFirst();
        var art = new GuideMemories.Snapshot("art-quiet", "art", "bakery", "quiet", 15, "test", List.of());
        var input = BaysAgent.decisionInput(bakery, art, "meal");
        assertTrue(input.contains("Traveler interest: bakery"));
        assertTrue(input.contains("dietary safety"));
        assertEquals("prefer", BaysAgent.value(((DecisionAnswer.Choice) new LocalGuideDecisions()
                .ask(DecisionRequest.of(input, FIT)).answer("fit")).getOutcome()));
    }

    @Test void liveNameTagsAreLiteralAndDoNotInferCuisineOrDiet() {
        assertEquals("art", GuidePlaces.nameTag("Pérez Art Museum Miami", "sights"));
        assertEquals("science", GuidePlaces.nameTag("Frost Museum of Science", "sights"));
        assertEquals("unknown", GuidePlaces.nameTag("Noodle House", "meal"));
        assertEquals("bakery", GuidePlaces.nameTag("Little Bakery", "meal"));
    }

    @Test void hardFailureAndTypedFailureCannotEnterRanker() {
        var agent = new BaysAgent(new GuidePlaces("https://nominatim.openstreetmap.org"), null);
        var options = GuidePlaces.fixed("sights").options();
        var rejected = new BaysAgent.Assessment(options.get(0), "defer", "", "true", "prefer",
                "strong", "indoor", "test", 0, 0);
        var failed = new BaysAgent.Assessment(options.get(1), "eligible", "", "true",
                "failure:INVALID_RESPONSE", "strong", "indoor", "test", 0, 0);
        var accepted = new BaysAgent.Assessment(options.get(2), "eligible", "", "false", "consider",
                "possible", "unknown", "test", 0, 0);
        var ranked = agent.rank(new BaysAgent.Assessments(List.of(rejected, failed, accepted)));
        assertEquals(List.of("Riverside Garden"), ranked.values().stream().map(a -> a.option().name()).toList());
    }

    @Test void typedInterestMatchBreaksEqualFitWithoutReadingMemoryAgain() {
        var agent = new BaysAgent(new GuidePlaces("https://nominatim.openstreetmap.org"), null);
        var options = GuidePlaces.fixed("sights").options();
        var art = new BaysAgent.Assessment(options.get(0), "eligible", "", "true", "consider",
                "possible", "unknown", "test", 0, 0);
        var science = new BaysAgent.Assessment(options.get(1), "eligible", "", "false", "consider",
                "possible", "unknown", "test", 0, 0);
        assertEquals("Pocket Art Gallery", agent.rank(new BaysAgent.Assessments(List.of(science, art)))
                .values().getFirst().option().name());
    }
}
