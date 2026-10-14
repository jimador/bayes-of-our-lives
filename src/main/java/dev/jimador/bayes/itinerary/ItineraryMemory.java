package dev.jimador.bayes.itinerary;

import com.embabel.dice.proposition.Proposition;
import com.embabel.dice.proposition.PropositionRepository;
import com.embabel.dice.proposition.PropositionStatus;
import com.embabel.dice.proposition.store.InMemoryPropositionRepository;
import com.embabel.dice.proposition.store.JsonFilePropositionRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Exact-context Dice retrieval; optional consented preferences use Dice's supported JSON-file store. */
@Component
public class ItineraryMemory {
    public record Accepted(String context, String interests, String pace, List<String> factIds) {}
    private final PropositionRepository saved;
    private final Path file;

    @Autowired public ItineraryMemory(@Value("${bays.preference-store:.bayes-data/preferences.json}") String file) {
        this(Path.of(file));
    }
    ItineraryMemory(Path file) {
        try {
            this.file = file;
            if (file.getParent() != null) {
                boolean newDirectory = !Files.exists(file.getParent());
                Files.createDirectories(file.getParent());
                if (newDirectory || file.getParent().endsWith(".bayes-data"))
                    restrict(file.getParent(), true);
            }
            this.saved = new JsonFilePropositionRepository(file);
            if (Files.exists(file)) restrict(file, false);
        } catch (Exception failure) {
            throw new IllegalStateException("The local Dice preference store is unavailable", failure);
        }
    }

    /** A saved scope is used only with an explicit request and a known opaque profile ID. */
    public Optional<Accepted> previous(String profileId) {
        if (profileId == null) return Optional.empty();
        String context = "bayes-profile-" + profileId;
        List<Proposition> retrieved = saved.findByContextIdValue(context);
        if (retrieved.isEmpty()) return Optional.empty();
        return Optional.of(accept(context, retrieved));
    }

    public ItineraryRequest resolve(ItineraryRequest request) {
        if (!request.useRememberedPreferences()) return request;
        var previous = previous(request.profileId());
        return previous.map(a -> request.resolvedPreferences(a.interests(), a.pace())).orElse(request);
    }

    public Accepted rememberForThisOuting(ItineraryRequest request) {
        boolean persistent = request.rememberPreferences() || request.useRememberedPreferences();
        PropositionRepository repository = persistent ? saved : new InMemoryPropositionRepository();
        String context = persistent ? "bayes-profile-" + request.profileId() : "bayes-outing-" + UUID.randomUUID();
        if (request.rememberPreferences() || !request.useRememberedPreferences()) {
            Instant now = Instant.now();
            Map<String, String> values = Map.of("interests", request.safeInterests(), "pace", request.pace());
            for (var entry : values.entrySet()) {
                repository.save(Proposition.create(context + "-" + entry.getKey(), context,
                        "Confirmed preference for a Bayes outing", List.of(), 1.0, 0.0, 0.5,
                        "confirmed outing input", List.of(), now, now, now, PropositionStatus.ACTIVE)
                        .withMetadataValue("key", entry.getKey())
                        .withMetadataValue("value", entry.getValue()));
                if (persistent) try { restrict(file, false); }
                catch (java.io.IOException failure) {
                    throw new IllegalStateException("Could not protect local Dice preferences", failure);
                }
            }
        }
        return accept(context, repository.findByContextIdValue(context));
    }

    private static void restrict(Path path, boolean directory) throws java.io.IOException {
        try {
            Files.setPosixFilePermissions(path, directory
                    ? java.util.Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE)
                    : java.util.Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) { /* Non-POSIX filesystems use their own ACLs. */ }
    }

    private static Accepted accept(String context, List<Proposition> retrieved) {
        Map<String, String> accepted = new java.util.HashMap<>();
        List<String> ids = new ArrayList<>();
        for (var fact : retrieved) {
            String key = fact.getId().equals(context + "-interests") ? "interests"
                    : fact.getId().equals(context + "-pace") ? "pace" : null;
            if (key == null) continue;
            Object raw = fact.getMetadata().get("value");
            if (!context.equals(fact.getContextIdValue()) || fact.getStatus() != PropositionStatus.ACTIVE
                    || fact.getConfidence() < 0.8 || !key.equals(fact.getMetadata().get("key"))
                    || !(raw instanceof String text) || text.isBlank()
                    || accepted.putIfAbsent(key, text) != null)
                throw new IllegalStateException("Confirmed Dice preference was malformed");
            ids.add(fact.getId());
        }
        if (accepted.size() != 2) throw new IllegalStateException("Confirmed Dice preferences were incomplete");
        ids.sort(String::compareTo);
        return new Accepted(context, accepted.get("interests"), accepted.get("pace"), List.copyOf(ids));
    }
}
