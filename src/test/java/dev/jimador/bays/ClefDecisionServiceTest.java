package dev.jimador.bays;

import com.embabel.common.ai.classification.ClassificationRequest;
import com.embabel.common.ai.classification.ClassificationResult;
import com.embabel.common.ai.classification.ClassificationSpec;
import com.embabel.common.ai.classification.FailureReason;
import com.embabel.common.ai.decision.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ClefDecisionServiceTest {
    @TempDir Path temporary;
    private static final DecisionSpec QUESTIONS = DecisionSpec.builder()
            .proposition("interest-match", q -> q.asking("Exact interest match?"))
            .choice("fit", q -> q.asking("Fit?")
                    .option("prefer", "Best fit").option("consider", "Possible").option("pass", "No fit"))
            .rating("strength", q -> q.asking("Strength?")
                    .level("strong", "Strong").level("possible", "Possible").level("weak", "Weak"))
            .build();
    private static final ClassificationSpec SETTING = ClassificationSpec.builder()
            .asking("Setting?").category("indoor", "Inside").category("outdoor", "Outside")
            .category("unknown", "Unknown").build();

    @Test void mapsOneShotBridgeAnswersToEmbabelTypedOutcomes() throws Exception {
        Path script = temporary.resolve("good.py");
        Files.writeString(script, """
                import json,sys
                request=json.load(sys.stdin)
                answers=[]
                for question in request['questions']:
                    qid=question['id']
                    if question['kind']=='proposition': ids=['true','false']; selected='true'
                    else:
                        options=question.get('options',question.get('levels'))
                        ids=[option['id'] for option in options]
                        selected={'fit':'prefer','strength':'strong','setting':'indoor'}[qid]
                    answers.append({'id':qid,'kind':question['kind'],'selected_id':selected,
                        'confidence':1.0,'probabilities':{name:(1.0 if name==selected else 0.0) for name in ids}})
                print(json.dumps({'ok':True,'input_tokens':100,'inference_ms':1.0,'wall_ms':2.0,'answers':answers}))
                """);
        var service = new ClefDecisionService("python3", script.toString());
        var response = service.ask(DecisionRequest.of("synthetic venue and validated Dice fields", QUESTIONS));
        assertNull(response.getRequestFailure());
        assertTrue(((PropositionResult.Answered) ((DecisionAnswer.Proposition) response.answer("interest-match"))
                .getOutcome()).getAnswer());
        assertEquals("prefer", BayesAgent.value(((DecisionAnswer.Choice) response.answer("fit")).getOutcome()));
        var rating = (RatingResult.Answered) ((DecisionAnswer.Rating) response.answer("strength")).getOutcome();
        assertEquals("strong", rating.getSelectedLevelId());
        assertEquals(3, rating.getDistribution().size());
        assertEquals("indoor", BayesAgent.value(service.classify(ClassificationRequest.of("venue", SETTING))));
    }

    @Test void missingArtifactAndMalformedResponseFailClosed() throws Exception {
        var missing = new ClefDecisionService("python3", temporary.resolve("missing.py").toString());
        assertEquals(FailureReason.UNAVAILABLE,
                missing.ask(DecisionRequest.of("state", QUESTIONS)).getRequestFailure());
        assertInstanceOf(ClassificationResult.Failure.class,
                missing.classify(ClassificationRequest.of("state", SETTING)));
        Path bad = temporary.resolve("bad.py");
        Files.writeString(bad, "import sys\nsys.stdin.read()\nprint('not-json')\n");
        var malformed = new ClefDecisionService("python3", bad.toString());
        assertEquals(FailureReason.INVALID_RESPONSE,
                malformed.ask(DecisionRequest.of("state", QUESTIONS)).getRequestFailure());
    }
}
