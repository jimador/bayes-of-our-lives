package dev.jimador.bays;

import com.embabel.dice.proposition.Proposition;
import com.embabel.dice.proposition.PropositionStatus;
import com.embabel.dice.proposition.store.InMemoryPropositionRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Dice capture and exact-context retrieval of confirmed, non-sensitive outing preferences. */
@Component
public class ItineraryMemory {
    public record Accepted(String context, String interests, String pace, List<String> factIds) {}

    public Accepted rememberForThisOuting(ItineraryRequest request) {
        // A per-run Dice store prevents accidental persistence of a visitor's submitted profile.
        var repository = new InMemoryPropositionRepository();
        String context = "bayes-outing-" + UUID.randomUUID();
        Instant now = Instant.now();
        Map<String, String> values = Map.of("interests", request.safeInterests(), "pace", request.pace());
        for (var entry : values.entrySet()) {
            String id = context + "-" + entry.getKey();
            repository.save(Proposition.create(id, context, "Confirmed preference for this outing",
                    List.of(), 1.0, 0.0, 0.5, "confirmed outing input", List.of(), now, now, now,
                    PropositionStatus.ACTIVE)
                    .withMetadataValue("key", entry.getKey()).withMetadataValue("value", entry.getValue()));
        }
        Map<String, String> accepted = new java.util.HashMap<>();
        List<String> ids = new ArrayList<>();
        for (var fact : repository.findByContextIdValue(context)) {
            String key = fact.getId().equals(context + "-interests") ? "interests"
                    : fact.getId().equals(context + "-pace") ? "pace" : null;
            if (key == null) continue;
            Object raw = fact.getMetadata().get("value");
            if (fact.getStatus() != PropositionStatus.ACTIVE || fact.getConfidence() < 0.8
                    || !key.equals(fact.getMetadata().get("key")) || !(raw instanceof String text)
                    || !text.equals(values.get(key)) || accepted.putIfAbsent(key, text) != null)
                throw new IllegalStateException("Confirmed Dice preference was malformed");
            ids.add(fact.getId());
        }
        if (accepted.size() != 2) throw new IllegalStateException("Confirmed Dice preferences were incomplete");
        ids.sort(String::compareTo);
        return new Accepted(context, accepted.get("interests"), accepted.get("pace"), List.copyOf(ids));
    }
}
