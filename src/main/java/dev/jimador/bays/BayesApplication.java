package dev.jimador.bays;

import com.embabel.common.ai.decision.DecisionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/** Bays CLI and optional loopback web app. */
@SpringBootApplication
public class BayesApplication {
    public static void main(String[] args) {
        String engine = java.util.Arrays.stream(args).filter(a -> a.startsWith("--demo.engine="))
                .map(a -> a.substring("--demo.engine=".length())).findFirst().orElse("local");
        boolean graph = java.util.Arrays.asList(args).contains("--demo.dice.enabled=true");
        boolean web = !java.util.Arrays.asList(args).contains("--bays.cli=true")
                && !java.util.Arrays.asList(args).contains("--bays.web=false");
        var app = new SpringApplication(BayesApplication.class);
        OwnedNeo4j owned = graph ? new OwnedNeo4j() : null;
        if (owned != null) app.addInitializers(owned);
        // Embabel registers a default LLM even when this GOAP path only calls local/JEV decisions.
        if (!engine.equals("openai") && (System.getenv("OPENAI_API_KEY") == null
                || System.getenv("OPENAI_API_KEY").isBlank()))
            app.setDefaultProperties(java.util.Map.of("embabel.agent.platform.models.openai.api-key", "unused-local-mode"));
        try {
            String[] launchArgs = web ? java.util.stream.Stream.concat(java.util.Arrays.stream(args),
                    java.util.stream.Stream.of("--spring.main.web-application-type=servlet", "--bays.web=true"))
                    .toArray(String[]::new) : args;
            var context = app.run(launchArgs);
            if (!web) SpringApplication.exit(context);
        } catch (RuntimeException failure) {
            if (owned != null) owned.close();
            throw failure;
        }
    }

    @Bean("baysDecisionService")
    DecisionService decisionService(@Value("${demo.engine:local}") String engine, BayesDecisionServices services) {
        // The named service preserves the CLI configuration; web requests resolve by selected engine.
        return services.forEngine(engine);
    }
}
