package dev.jimador.bayes.itinerary;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ItineraryPrivacyTest {
    @TempDir Path temporary;
    @Test void dicePreferencesReachSafeModelAndDecisionDtosWhileOriginAndMedicalTextStayLocal() throws Exception {
        var request = new ItineraryRequest("City", "PRIVATE_ORIGIN_SENTINEL", LocalDate.of(2030, 10, 15),
                LocalTime.of(7, 0), LocalTime.of(14, 0), true, false, false, true, false,
                2, 0, "art", "steady", false, "PRIVATE_MEDICAL_SENTINEL", "local", true, true,
                null, false, false, "seafood, cuban", "italian");
        var memory = new ItineraryMemories(temporary.resolve("preferences.json")).rememberForThisOuting(request);
        var brief = ItineraryBrief.from(request, memory);
        String serialized = new ObjectMapper().findAndRegisterModules().writeValueAsString(DecisionBrief.from(brief, List.of()));
        assertTrue(serialized.contains("art"));
        assertTrue(serialized.contains("steady"));
        assertTrue(serialized.contains("seafood"));
        assertFalse(serialized.contains("PRIVATE_ORIGIN_SENTINEL"));
        assertFalse(serialized.contains("PRIVATE_MEDICAL_SENTINEL"));
        assertFalse(serialized.contains(memory.context()));
        assertFalse(serialized.contains("dietary"));
    }
}
