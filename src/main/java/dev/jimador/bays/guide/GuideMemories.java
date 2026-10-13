package dev.jimador.bays.guide;

import com.embabel.dice.proposition.Proposition;
import com.embabel.dice.proposition.PropositionRepository;
import com.embabel.dice.proposition.PropositionStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/** Seeds and reads fictional Dice propositions. Raw proposition prose never enters a decision request. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class GuideMemories implements CommandLineRunner {
    public record Fact(String id, String context, String status, String source) {}
    public record Snapshot(String profile, String sightInterest, String mealInterest,
                           String ambience, int comfortableDriveMinutes, String store, List<Fact> facts) {
        public String interestFor(String intent) {
            return switch (intent) {
                case "sights" -> sightInterest;
                case "meal" -> mealInterest;
                default -> throw new IllegalArgumentException("intent must be sights or meal");
            };
        }
        public String decisionFields(String intent) {
            return "Traveler interest: " + interestFor(intent)
                    + "\nTraveler ambience: " + ambience
                    + "\nComfortable one-way drive minutes: " + comfortableDriveMinutes;
        }
        public String provenance() {
            return "context=" + context(profile) + " store=" + store + " facts=" + facts;
        }
    }

    private static final Set<String> PROFILES = Set.of("art-quiet", "science-lively");
    private static final List<String> KEYS = List.of("sightInterest", "mealInterest", "ambience", "comfortableDriveMinutes");
    private final PropositionRepository repository;

    public GuideMemories(PropositionRepository repository) { this.repository = repository; }

    @Override public void run(String... args) {
        seed("art-quiet", "art", "bakery", "quiet", 15);
        seed("science-lively", "science", "spicy", "lively", 30);
    }

    private void seed(String profile, String sights, String meal, String ambience, int drive) {
        Map<String, Object> values = Map.of("sightInterest", sights, "mealInterest", meal,
                "ambience", ambience, "comfortableDriveMinutes", drive);
        Instant now = Instant.now();
        for (String key : KEYS) {
            var proposition = Proposition.create(id(profile, key), context(profile),
                    "This fictional traveler has " + key + " preference " + values.get(key) + ".",
                    List.of(), 1.0, 0.0, 0.5, "synthetic Bays seed", List.of(), now, now, now,
                    PropositionStatus.ACTIVE)
                    .withMetadataValue("memoryKey", key).withMetadataValue("memoryValue", values.get(key));
            repository.save(proposition);
        }
    }

    public Snapshot read(String profile) {
        requireProfile(profile);
        return project(profile, repository.findByContextIdValue(context(profile)),
                repository.getStoreType().toString().toLowerCase(java.util.Locale.ROOT));
    }

    /** Exact-ID projection also works with fixture propositions in unit tests. */
    static Snapshot project(String profile, List<Proposition> retrieved, String store) {
        requireProfile(profile);
        Map<String, Object> values = new HashMap<>();
        List<Fact> facts = new ArrayList<>();
        for (var proposition : retrieved) {
            if (!proposition.getContextIdValue().equals(context(profile))) continue;
            String key = KEYS.stream().filter(k -> proposition.getId().equals(id(profile, k)))
                    .findFirst().orElse(null);
            if (key == null) continue; // Irrelevant memories are never prompt instructions.
            if (proposition.getStatus() != PropositionStatus.ACTIVE || proposition.getConfidence() < 0.8)
                throw new IllegalStateException("Inactive or low-confidence Dice memory: " + proposition.getId());
            Map<String, Object> metadata = proposition.getMetadata();
            if (!key.equals(metadata.get("memoryKey")) || values.putIfAbsent(key, metadata.get("memoryValue")) != null)
                throw new IllegalStateException("Duplicate or malformed Dice memory: " + proposition.getId());
            facts.add(new Fact(proposition.getId(), proposition.getContextIdValue(),
                    proposition.getStatus().name(), proposition.getReasoning()));
        }
        if (values.size() != KEYS.size()) throw new IllegalStateException("Fictional Dice profile is incomplete");
        String sights = tag(values.get("sightInterest"));
        String meal = tag(values.get("mealInterest"));
        String ambience = tag(values.get("ambience"));
        if (!Set.of("quiet", "lively").contains(ambience)) throw new IllegalStateException("Invalid ambience memory");
        Object rawDrive = values.get("comfortableDriveMinutes");
        if (!(rawDrive instanceof Number number) || number.doubleValue() != number.intValue()
                || number.intValue() < 5 || number.intValue() > 60)
            throw new IllegalStateException("Invalid drive memory");
        facts.sort(java.util.Comparator.comparing(Fact::id));
        return new Snapshot(profile, sights, meal, ambience, number.intValue(), store, List.copyOf(facts));
    }

    private static String tag(Object value) {
        if (!(value instanceof String text) || !text.matches("[a-z][a-z-]{2,24}"))
            throw new IllegalStateException("Invalid interest memory");
        return text;
    }
    private static void requireProfile(String profile) {
        if (!PROFILES.contains(profile)) throw new IllegalArgumentException("profile must be art-quiet or science-lively");
    }
    public static String context(String profile) { return "bays-fictional-" + profile; }
    static String id(String profile, String key) { return "bays-" + profile + "-" + key; }
}
