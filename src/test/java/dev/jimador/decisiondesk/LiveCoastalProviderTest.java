package dev.jimador.decisiondesk;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class LiveCoastalProviderTest {
    @Test void parsesMinutePrecisionUtcHoursReturnedByOpenMeteo() {
        assertEquals(Instant.parse("2026-10-11T01:00:00Z"), LiveCoastalProvider.utcHour("2026-10-11T01:00"));
    }
}
