package dev.jimador.bays.itinerary;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Opt-in local capture. Raw decision questions never go into published result files. */
@Component
public class ReplayCaptureStore {
    private final Path path;
    private final ObjectMapper mapper;
    public ReplayCaptureStore(@Value("${bays.replay.capture-file:}") String filename) {
        this.path = filename.isBlank() ? null : Path.of(filename).toAbsolutePath().normalize();
        this.mapper = new ObjectMapper();
        if (path != null && !path.startsWith(Path.of(".bayes-data").toAbsolutePath().normalize()))
            throw new IllegalArgumentException("Replay capture must stay under the ignored .bayes-data directory");
    }
    public void save(DecisionReplay.Capture capture, ItineraryRequest request) {
        if (path == null) return;
        if (request.hasDietaryNeed() || !request.safeInterests().matches("[A-Za-z ,'-]{1,100}"))
            return; // Skip unsafe capture without interrupting the outing. The writer is opt-in.
        try {
            Path root = Path.of(".bayes-data").toAbsolutePath().normalize();
            if (Files.isSymbolicLink(root)) throw new IllegalArgumentException("Replay capture cannot use symbolic links");
            Files.createDirectories(path.getParent());
            if (Files.isSymbolicLink(path.getParent()) || Files.isSymbolicLink(path))
                throw new IllegalArgumentException("Replay capture cannot use symbolic links");
            try { Files.setPosixFilePermissions(path.getParent(), Set.of(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE)); }
            catch (UnsupportedOperationException ignored) { }
            mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), capture);
            try { Files.setPosixFilePermissions(path, Set.of(PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE)); }
            catch (UnsupportedOperationException ignored) { }
        } catch (java.io.IOException failure) { throw new IllegalStateException("Could not save local replay capture", failure); }
    }
}
