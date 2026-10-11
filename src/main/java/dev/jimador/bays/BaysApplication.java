package dev.jimador.bays;

import com.embabel.agent.spi.decision.LlmDecisionServiceFactory;
import com.embabel.agent.typesafe.TypeSafeModelFactory;
import com.embabel.common.ai.decision.DecisionService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/** Temporary Java/package identifiers; the user-facing app name is Bays. */
@SpringBootApplication
public class BaysApplication {
    public static void main(String[] args) {
        String engine = java.util.Arrays.stream(args).filter(a -> a.startsWith("--demo.engine="))
                .map(a -> a.substring("--demo.engine=".length())).findFirst().orElse("local");
        boolean graph = java.util.Arrays.asList(args).contains("--demo.dice.enabled=true");
        var app = new SpringApplication(BaysApplication.class);
        OwnedNeo4j owned = graph ? new OwnedNeo4j() : null;
        if (owned != null) app.addInitializers(owned);
        // Embabel registers a default LLM even when this GOAP path only calls local/JEV decisions.
        if (!engine.equals("openai") && (System.getenv("OPENAI_API_KEY") == null
                || System.getenv("OPENAI_API_KEY").isBlank()))
            app.setDefaultProperties(java.util.Map.of("embabel.agent.platform.models.openai.api-key", "unused-local-mode"));
        try {
            var context = app.run(args);
            SpringApplication.exit(context);
        } catch (RuntimeException failure) {
            if (owned != null) owned.close();
            throw failure;
        }
    }

    @Bean("baysDecisionService")
    DecisionService decisionService(@Value("${demo.engine:local}") String engine,
            @Value("${demo.openai-model:gpt-4.1}") String model,
            @Value("${bays.clef-python:python3}") String clefPython,
            @Value("${bays.clef-bridge-path:clef/clef_bridge.py}") String clefBridge,
            ObjectProvider<LlmDecisionServiceFactory> llmFactory) {
        return switch (engine) {
            case "local" -> new LocalGuideDecisions();
            case "jev" -> new TypeSafeModelFactory(() -> requiredKey("TYPESAFE_API_KEY")).build();
            case "openai" -> llmFactory.getObject().decisionService(model);
            case "clef" -> new ClefDecisionService(clefPython, clefBridge);
            default -> throw new IllegalArgumentException("demo.engine must be local, jev, openai, or clef");
        };
    }

    private static String requiredKey(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }
}
