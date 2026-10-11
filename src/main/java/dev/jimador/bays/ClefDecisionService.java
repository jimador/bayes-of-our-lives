package dev.jimador.bays;

import com.embabel.common.ai.classification.ClassificationRequest;
import com.embabel.common.ai.classification.ClassificationResult;
import com.embabel.common.ai.classification.FailureReason;
import com.embabel.common.ai.classification.ModelProvenance;
import com.embabel.common.ai.decision.*;
import com.embabel.common.ai.decision.spi.QuestionSetExecution;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Experimental, local Core ML bridge. Bridge errors are typed failures, never approval. */
public final class ClefDecisionService implements DecisionService, QuestionSetExecution {
    private static final ModelProvenance MODEL = new ModelProvenance(
            "FluidInference/clef-text-0.6b-coreml", "CoreML-local-derived-L1024",
            "3c61a60954478ef385aa42d67d5764642dcc9135", null);
    private final ObjectMapper mapper = new ObjectMapper();
    private final String python;
    private final String script;

    public ClefDecisionService(String python, String script) {
        this.python = python;
        this.script = script;
    }
    @Override public String getName() { return "CLEF 0.6B experimental"; }
    @Override public String getProvider() { return "CoreML-local-derived-L1024"; }
    @Override public DecisionCapabilities capabilities() {
        return DecisionCapabilities.of(EnumSet.allOf(QuestionKind.class));
    }

    @Override public DecisionResponse askQuestionSet(DecisionRequest request) {
        List<Map<String, Object>> questions = new ArrayList<>();
        for (var q : request.getSpec().getQuestions()) {
            if (q instanceof PropositionQuestionSpec)
                questions.add(Map.of("id", q.getName(), "kind", "proposition", "instructions", q.getInstructions()));
            else if (q instanceof ChoiceQuestionSpec choice)
                questions.add(Map.of("id", q.getName(), "kind", "choice", "instructions", q.getInstructions(),
                        "options", choice.getOptions().stream().map(o -> Map.of("id", o.getId(),
                                "description", o.getDescription())).toList()));
            else if (q instanceof RatingQuestionSpec rating)
                questions.add(Map.of("id", q.getName(), "kind", "rating", "instructions", q.getInstructions(),
                        "levels", rating.getLevels().stream().map(o -> Map.of("id", o.getId(),
                                "description", o.getDescription())).toList()));
            else return DecisionResponse.failed(request.getSpec(), FailureReason.INVALID_RESPONSE);
        }
        var bridge = bridge(request.getInput(), questions);
        if (bridge.failure != null) return DecisionResponse.failed(request.getSpec(), bridge.failure);
        try {
            var byId = answersById(bridge.body);
            var builder = DecisionResponse.builder(request.getSpec());
            for (var q : request.getSpec().getQuestions()) {
                JsonNode answer = byId.path(q.getName());
                if (!answer.isObject()) throw new IllegalArgumentException("Missing answer");
                String selected = answer.path("selected_id").asText();
                double confidence = probability(answer.path("confidence"));
                if (q instanceof PropositionQuestionSpec proposition) {
                    if (!selected.equals("true") && !selected.equals("false")) throw new IllegalArgumentException("Bad Boolean");
                    double pTrue = probability(answer.path("probabilities").path("true"));
                    builder.answer(proposition, new PropositionResult.Answered(selected.equals("true"), MODEL, pTrue));
                } else if (q instanceof ChoiceQuestionSpec choice) {
                    builder.answer(choice, new ClassificationResult.Selected(selected, MODEL, confidence));
                } else if (q instanceof RatingQuestionSpec rating) {
                    List<LevelProbability> distribution = rating.getLevels().stream()
                            .map(level -> new LevelProbability(level.getId(),
                                    probability(answer.path("probabilities").path(level.getId())))).toList();
                    builder.answer(rating, new RatingResult.Answered(MODEL, selected, distribution, null, confidence));
                }
            }
            return builder.build();
        } catch (RuntimeException malformed) {
            return DecisionResponse.failed(request.getSpec(), FailureReason.INVALID_RESPONSE);
        }
    }

    @Override public PropositionResult assess(PropositionRequest request) {
        var bridge = bridge(request.getInput(), List.of(Map.of("id", "direct", "kind", "proposition",
                "instructions", request.getProposition())));
        if (bridge.failure != null) return new PropositionResult.Failure(bridge.failure);
        try {
            JsonNode answer = answersById(bridge.body).path("direct");
            String selected = answer.path("selected_id").asText();
            if (!selected.equals("true") && !selected.equals("false")) throw new IllegalArgumentException();
            return new PropositionResult.Answered(selected.equals("true"), MODEL,
                    probability(answer.path("probabilities").path("true")));
        } catch (RuntimeException malformed) { return new PropositionResult.Failure(FailureReason.INVALID_RESPONSE); }
    }

    @Override public ClassificationResult classify(ClassificationRequest request) {
        var question = Map.of("id", "setting", "kind", "classification", "instructions", request.getInstructions(),
                "options", request.getCategories().stream().map(c -> Map.of("id", c.getId(),
                        "description", c.getDescription())).toList());
        var bridge = bridge(request.getInput(), List.of(question));
        if (bridge.failure != null) return new ClassificationResult.Failure(bridge.failure);
        try {
            JsonNode answer = answersById(bridge.body).path("setting");
            return request.getSpec().selected(answer.path("selected_id").asText(), MODEL,
                    probability(answer.path("confidence")));
        } catch (RuntimeException malformed) { return new ClassificationResult.Failure(FailureReason.INVALID_RESPONSE); }
    }

    private record Bridge(JsonNode body, FailureReason failure) {}
    private Bridge bridge(String state, List<Map<String, Object>> questions) {
        Process process = null;
        try {
            byte[] payload = mapper.writeValueAsBytes(Map.of("state", state, "questions", questions));
            var builder = new ProcessBuilder(python, script).redirectError(ProcessBuilder.Redirect.DISCARD);
            process = builder.start();
            try (var stdin = process.getOutputStream()) { stdin.write(payload); }
            if (!process.waitFor(50, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor();
                System.err.println("clefBridgeFailure code=outer_timeout");
                return new Bridge(null, FailureReason.UNAVAILABLE);
            }
            byte[] output = process.getInputStream().readAllBytes();
            if (output.length > 65536) throw new IllegalArgumentException("Oversize bridge response");
            JsonNode json = mapper.readTree(output);
            if (process.exitValue() != 0 || !json.path("ok").asBoolean(false)) {
                String code = json.path("error").path("code").asText("bridge_failure");
                System.err.println("clefBridgeFailure code=" + code);
                return new Bridge(null, code.equals("invalid_request") || code.equals("invalid_output")
                        || code.equals("input_too_long") ? FailureReason.INVALID_RESPONSE : FailureReason.UNAVAILABLE);
            }
            if (!json.path("answers").isArray()) throw new IllegalArgumentException("Missing answers");
            System.out.println("clefBridge model=FluidInference/clef-text-0.6b-coreml"
                    + " variant=local-single-function-L1024 inputTokens=" + json.path("input_tokens").asInt()
                    + " modelInferenceMs=" + json.path("inference_ms").asDouble()
                    + " bridgeWallMs=" + json.path("wall_ms").asDouble()
                    + " answers=" + json.path("answers"));
            return new Bridge(json, null);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            if (process != null) process.destroyForcibly();
            System.err.println("clefBridgeFailure code=interrupted");
            return new Bridge(null, FailureReason.UNAVAILABLE);
        } catch (com.fasterxml.jackson.core.JsonProcessingException malformed) {
            if (process != null) process.destroyForcibly();
            System.err.println("clefBridgeFailure code=invalid_json");
            return new Bridge(null, FailureReason.INVALID_RESPONSE);
        } catch (Exception failure) {
            if (process != null) process.destroyForcibly();
            System.err.println("clefBridgeFailure code=bridge_io_or_protocol");
            return new Bridge(null, FailureReason.UNAVAILABLE);
        }
    }
    private static double probability(JsonNode node) {
        if (!node.isNumber() || !Double.isFinite(node.asDouble()) || node.asDouble() < 0 || node.asDouble() > 1)
            throw new IllegalArgumentException("Invalid probability");
        return node.asDouble();
    }
    private static JsonNode answersById(JsonNode response) {
        var answers = response.path("answers");
        var mapped = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        for (var answer : answers) {
            String id = answer.path("id").asText();
            if (id.isBlank() || mapped.has(id)) throw new IllegalArgumentException("Duplicate or blank answer ID");
            mapped.set(id, answer);
        }
        return mapped;
    }
}
