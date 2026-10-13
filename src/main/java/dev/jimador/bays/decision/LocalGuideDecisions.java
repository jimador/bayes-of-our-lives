package dev.jimador.bays.decision;

import com.embabel.common.ai.classification.ClassificationRequest;
import com.embabel.common.ai.classification.ClassificationResult;
import com.embabel.common.ai.classification.ModelProvenance;
import com.embabel.common.ai.decision.*;
import com.embabel.common.ai.decision.spi.RatingAssessment;
import java.util.EnumSet;
import java.util.Set;

/** Transparent code implementation of the exact same typed request contract. */
public final class LocalGuideDecisions implements DecisionService, RatingAssessment {
    private static final ModelProvenance PROVENANCE = new ModelProvenance("bays-rules-v1", "local");
    @Override public String getName() { return "bays-rules-v1"; }
    @Override public String getProvider() { return "local"; }
    @Override public DecisionCapabilities capabilities() {
        return DecisionCapabilities.of(EnumSet.allOf(QuestionKind.class));
    }
    @Override public PropositionResult assess(PropositionRequest request) {
        return new PropositionResult.Answered(matches(field(request.getInput(), "Venue interest tag"),
                field(request.getInput(), "Traveler interest")), PROVENANCE);
    }
    @Override public ClassificationResult classify(ClassificationRequest request) {
        Set<String> ids = request.getSpec().getCategories().stream().map(c -> c.getId()).collect(java.util.stream.Collectors.toSet());
        if (ids.contains("prefer")) return request.getSpec().selected(fit(request.getInput()), PROVENANCE);
        String setting = field(request.getInput(), "Venue setting");
        if (!Set.of("indoor", "outdoor").contains(setting)) setting = "unknown";
        return request.getSpec().selected(setting, PROVENANCE);
    }
    @Override public RatingResult rate(String input, RatingQuestionSpec question) {
        String strength = switch (fit(input)) {
            case "prefer" -> "strong";
            case "consider" -> "possible";
            default -> "weak";
        };
        return new RatingResult.Answered(PROVENANCE, strength);
    }
    private static String fit(String input) {
        boolean interest = matches(field(input, "Venue interest tag"), field(input, "Traveler interest"));
        boolean ambience = matches(field(input, "Venue ambience"), field(input, "Traveler ambience"));
        int drive = parse(field(input, "Base one-way drive minutes"));
        int comfort = parse(field(input, "Comfortable one-way drive minutes"));
        boolean comfortable = drive >= 0 && comfort >= 0 && drive <= comfort;
        if (interest && ambience && comfortable) return "prefer";
        return interest || ambience || comfortable ? "consider" : "pass";
    }
    private static boolean matches(String actual, String wanted) {
        return !actual.isBlank() && !actual.equals("unknown") && actual.equals(wanted);
    }
    private static int parse(String value) {
        try { return Integer.parseInt(value); } catch (NumberFormatException failure) { return -1; }
    }
    private static String field(String input, String key) {
        for (String line : input.split("\\R"))
            if (line.startsWith(key + ": ")) return line.substring(key.length() + 2).trim();
        return "";
    }
}
