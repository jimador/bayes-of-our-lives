package dev.jimador.bayes.decision;

import com.embabel.common.ai.classification.ClassificationRequest;
import com.embabel.common.ai.decision.DecisionRequest;
import dev.jimador.bayes.itinerary.BayesAgent;
import dev.jimador.bayes.itinerary.ItineraryMemories;
import dev.jimador.bayes.itinerary.ItineraryPlanner;
import dev.jimador.bayes.itinerary.ItineraryRequest;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

class ItineraryDecisionsTest {
    @Test void agentUsesTheExactSelectedServiceForDecisionAndClassification() {
        var service = spy(new LocalItineraryDecisions());
        var agent = new BayesAgent(mock(ItineraryMemories.class), null, null, null, null, null);
        var request = new ItineraryRequest("Miami", "Downtown", LocalDate.of(2030, 10, 15),
                LocalTime.of(10, 0), LocalTime.of(14, 0), false, false, false,
                false, false, 2, 0, "art", "steady", false, "private allergy marker", "jev", true, true);
        var profile = new BayesAgent.OutingProfile(new ItineraryMemories.Accepted("ctx", "art", "steady", List.of()), request);
        var alternative = new ItineraryPlanner.Alternative("a", "Art", "provisional", List.of(), List.of(), 0);
        var review = agent.decideItinerary(profile, new BayesAgent.PointOfInterestFindings(null, List.of()),
                new BayesAgent.ProposedTravelPlan(List.of(alternative)), new BayesAgent.SelectedDecision(service));
        assertEquals("a", review.choice());
        assertEquals("sight-only", review.classification());
        var decision = ArgumentCaptor.forClass(DecisionRequest.class);
        var classification = ArgumentCaptor.forClass(ClassificationRequest.class);
        verify(service).ask(decision.capture());
        verify(service, times(2)).classify(classification.capture());
        assertEquals(decision.getValue().getInput() + "\nSelected itinerary type: sight-only",
                classification.getAllValues().getLast().getInput());
        assertFalse(decision.getValue().getInput().contains("private allergy marker"));
        assertFalse(decision.getValue().getInput().contains(service.toString()));
    }
    @Test void localRatingPenalizesDislikedCuisineEvenWhenAnAlternativeIsChosen() {
        var local = new LocalItineraryDecisions();
        String input = "Alternative a status: provisional\nAlternative a open questions: 0\n"
                + "Alternative a disliked food stops: 1\nAlternative a liked food stops: 0\n"
                + "Alternative b status: invalid\nAlternative b open questions: 5";
        var result = local.rate(input, mock(com.embabel.common.ai.decision.RatingQuestionSpec.class));
        assertEquals("weak", ((com.embabel.common.ai.decision.RatingResult.Answered) result).getSelectedLevelId());
    }

}
