package dev.jimador.bayes.config;

import com.embabel.common.ai.model.LlmOptions;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LanguageModelConfiguration {
    @Bean("researcherLlm") LlmOptions researcher(@Value("${bayes.models.researcher:gpt-4.1}") String model) {
        return LlmOptions.withModel(model).withMaxTokens(3000).withTimeout(Duration.ofSeconds(45));
    }
    @Bean("plannerLlm") LlmOptions planner(@Value("${bayes.models.planner:gpt-4.1}") String model) {
        return LlmOptions.withModel(model).withMaxTokens(2500).withTimeout(Duration.ofSeconds(60));
    }
}
