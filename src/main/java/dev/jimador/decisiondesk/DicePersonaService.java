package dev.jimador.decisiondesk;

import com.embabel.dice.proposition.Proposition;
import com.embabel.dice.proposition.PropositionRepository;
import com.embabel.dice.proposition.PropositionStatus;
import com.embabel.dice.proposition.PropositionStoreType;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/** Persists and reads an explicitly fictional preference profile through Dice propositions. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
final class DicePersonaService implements CommandLineRunner {
    static final String CONTEXT = "fictional-paddler";
    private final PropositionRepository repository;
    private final ResourceLoader resources;
    private final boolean enabled;
    private final String seedFile;

    DicePersonaService(PropositionRepository repository, ResourceLoader resources,
                       @Value("${demo.dice.enabled:false}") boolean enabled,
                       @Value("${demo.persona-file:classpath:demo-persona.properties}") String seedFile) {
        this.repository = repository;
        this.resources = resources;
        this.enabled = enabled;
        this.seedFile = seedFile;
    }

    @Override public void run(String... args) throws IOException {
        if (!enabled) return;
        if (repository.getStoreType() != PropositionStoreType.STORED)
            throw new IllegalStateException("Dice graph repository was not selected");
        Properties seed = new Properties();
        try (var stream = resources.getResource(seedFile).getInputStream()) { seed.load(stream); }
        String type = seed.getProperty("preferredWaterwayType", "intracoastal").trim();
        if (!type.equals("intracoastal") && !type.equals("ocean")) throw new IllegalArgumentException("Waterway type must be intracoastal or ocean");
        save("preferred-waterway", "This fictional paddler prefers " + type + " access areas.", "preferredWaterwayType", type);
        for (String key : List.of("preferredTypeBonus", "shortDriveWeight", "lowWindWeight")) {
            double value = Double.parseDouble(seed.getProperty(key));
            if (!Double.isFinite(value) || value < 0 || value > 1) throw new IllegalArgumentException("Invalid " + key);
            save(key, "This fictional paddler has " + key + " of " + value + ".", key, value);
        }
    }

    private void save(String id, String text, String key, Object value) {
        Instant now = Instant.now();
        var proposition = Proposition.create("demo-" + id, CONTEXT, text, List.of(), 1.0, 0.0,
                0.5, "synthetic seed", List.of(), now, now, now, PropositionStatus.ACTIVE)
                .withMetadataValue("preferenceKey", key).withMetadataValue("preferenceValue", value);
        repository.save(proposition);
    }

    PaddleComparisonAgent.PreferenceProfile read(String fallbackType) {
        if (!enabled) return new PaddleComparisonAgent.PreferenceProfile("request-default", fallbackType, 0.15, 0.01, 0.01);
        Map<String, Object> values = new java.util.HashMap<>();
        for (var proposition : repository.findByContextIdValue(CONTEXT)) {
            var metadata = proposition.getMetadata();
            if (metadata.get("preferenceKey") instanceof String key) values.put(key, metadata.get("preferenceValue"));
        }
        if (values.size() != 4) throw new IllegalStateException("Dice fictional profile is incomplete");
        return new PaddleComparisonAgent.PreferenceProfile("dice-graph", (String) values.get("preferredWaterwayType"),
                ((Number) values.get("preferredTypeBonus")).doubleValue(),
                ((Number) values.get("shortDriveWeight")).doubleValue(),
                ((Number) values.get("lowWindWeight")).doubleValue());
    }
}
