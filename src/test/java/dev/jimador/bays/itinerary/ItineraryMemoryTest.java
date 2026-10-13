package dev.jimador.bays.itinerary;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ItineraryMemoryTest {
    @TempDir Path temporary;
    private ItineraryRequest request(String id, String interests, String pace, boolean save, boolean recall) {
        return new ItineraryRequest("Miami", "Downtown", LocalDate.now().plusDays(1),
                LocalTime.of(10, 0), LocalTime.of(14, 0), false, false, false,
                false, false, 2, 0, interests, pace, false, "", "local", true, false,
                id, save, recall);
    }
    @Test void preferencesPersistAcrossRepositoryInstancesAndRemainScoped() throws Exception {
        Path file = temporary.resolve("dice/preferences.json");
        String owner = UUID.randomUUID().toString(), another = UUID.randomUUID().toString();
        var first = new ItineraryMemory(file);
        first.rememberForThisOuting(request(owner, "art and gardens", "relaxed", true, false));
        try {
            var permissions = Files.getPosixFilePermissions(file);
            assertEquals(java.util.Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                    permissions);
        } catch (UnsupportedOperationException ignored) { /* The filesystem does not expose POSIX modes. */ }
        var restarted = new ItineraryMemory(file);
        assertEquals("art and gardens", restarted.previous(owner).orElseThrow().interests());
        assertTrue(restarted.previous(another).isEmpty());
        var recalled = restarted.resolve(request(owner, "", null, false, true));
        assertEquals("relaxed", recalled.pace());
        assertEquals("art and gardens", recalled.safeInterests());
        assertEquals("art and gardens", restarted.rememberForThisOuting(recalled).interests());
    }
    @Test void noConsentLeavesNoPersistedPreference() {
        Path file = temporary.resolve("dice/preferences.json");
        String id = UUID.randomUUID().toString();
        new ItineraryMemory(file).rememberForThisOuting(request(id, "art", "steady", false, false));
        assertTrue(new ItineraryMemory(file).previous(id).isEmpty());
    }
    @Test void malformedProfileIdCannotSelectAnotherStorageScope() {
        assertThrows(IllegalArgumentException.class,
                () -> request("------------------------------------", "art", "steady", true, false).validateShape());
    }
}
