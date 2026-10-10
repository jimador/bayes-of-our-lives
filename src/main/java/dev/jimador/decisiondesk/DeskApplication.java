package dev.jimador.decisiondesk;

import com.embabel.agent.spi.decision.LlmDecisionServiceFactory;
import com.embabel.agent.typesafe.TypeSafeModelFactory;
import com.embabel.common.ai.decision.DecisionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class DeskApplication {
    public static void main(String[] args) {
        String engine = java.util.Arrays.stream(args)
                .filter(arg -> arg.startsWith("--demo.engine="))
                .map(arg -> arg.substring("--demo.engine=".length()))
                .findFirst().orElse("local");
        SpringApplication application = new SpringApplication(DeskApplication.class);
        // Embabel's platform still requires a registered LLM even for a code-only GOAP flow.
        // This inert value enables model registration; the local/Jev decision action does not invoke it.
        if (!engine.equals("openai") && System.getenv("OPENAI_API_KEY") == null) {
            application.setDefaultProperties(java.util.Map.of(
                    "embabel.agent.platform.models.openai.api-key", "unused-local-mode"));
        }
        application.run(args);
    }

    /** The entire engine switch lives here. No agent action changes between runs. */
    @Bean("activeDecisionService")
    DecisionService activeDecisionService(
            @Value("${demo.engine:local}") String engine,
            @Value("${demo.openai-model:gpt-4.1-mini}") String openAiModel,
            org.springframework.beans.factory.ObjectProvider<LlmDecisionServiceFactory> llmFactory) {
        return switch (engine) {
            case "local" -> new KeywordDecisionService();
            case "jev" -> new TypeSafeModelFactory(() -> requiredKey("TYPESAFE_API_KEY")).build();
            case "openai" -> llmFactory.getObject().decisionService(openAiModel);
            default -> throw new IllegalArgumentException("demo.engine must be local, jev, or openai");
        };
    }

    private static String requiredKey(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }
}
