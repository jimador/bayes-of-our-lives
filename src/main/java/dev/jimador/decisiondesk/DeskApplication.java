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
        SpringApplication.run(DeskApplication.class, args);
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
