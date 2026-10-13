package dev.jimador.bays.decision;

import com.embabel.agent.spi.decision.LlmDecisionServiceFactory;
import com.embabel.agent.typesafe.TypeSafeModelFactory;
import com.embabel.common.ai.decision.DecisionService;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Resolves a request's decision engine without storing keys or selection in global request state. */
@Component
public class BayesDecisionServices {
    private static final Set<String> ENGINES = Set.of("local", "jev", "openai");
    private final Map<String, DecisionService> services = new ConcurrentHashMap<>();
    private final ObjectProvider<LlmDecisionServiceFactory> llmFactory;
    private final String model;

    public BayesDecisionServices(ObjectProvider<LlmDecisionServiceFactory> llmFactory,
            @Value("${demo.openai-model:gpt-4.1}") String model) {
        this.llmFactory = llmFactory;
        this.model = model;
    }

    public DecisionService forEngine(String engine) {
        if (!ENGINES.contains(engine)) throw new IllegalArgumentException("engine must be local, jev, or openai");
        if (engine.equals("jev")) requiredKey("TYPESAFE_API_KEY");
        if (engine.equals("openai")) requiredKey("OPENAI_API_KEY");
        return services.computeIfAbsent(engine, this::create);
    }

    public DecisionService forItineraryEngine(String engine) {
        return engine.equals("local") ? new LocalItineraryDecisions() : forEngine(engine);
    }

    private DecisionService create(String engine) {
        return switch (engine) {
            case "local" -> new LocalGuideDecisions();
            case "jev" -> new TypeSafeModelFactory(() -> requiredKey("TYPESAFE_API_KEY")).build();
            case "openai" -> {
                requiredKey("OPENAI_API_KEY");
                yield llmFactory.getObject().decisionService(model);
            }
            default -> throw new IllegalArgumentException("Unsupported engine");
        };
    }

    private static String requiredKey(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new ModelUnavailableException(name + " is not configured on the server");
        return value;
    }

    public static class ModelUnavailableException extends RuntimeException {
        public ModelUnavailableException(String message) { super(message); }
    }
}
