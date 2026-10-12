package dev.jimador.bays;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class ItineraryTimezoneTest {
    private static ItineraryRequest request(LocalDate date, LocalTime start, LocalTime end) {
        return new ItineraryRequest("Honolulu", "Waikiki", date, start, end,
                false, false, false, false, false, 2, 0, "art", "steady", false,
                "", "local", true, true);
    }
    @Test void destinationDayDeterminesWhetherOutingIsPast() {
        ZoneId honolulu = ZoneId.of("Pacific/Honolulu");
        var gaps = new ArrayList<String>();
        assertDoesNotThrow(() -> ItineraryResearch.validateDestinationTime(
                request(LocalDate.now(honolulu).plusDays(1), LocalTime.of(10, 0),
                        LocalTime.of(14, 0)), honolulu.getId(), gaps));
        assertTrue(gaps.isEmpty());
        assertThrows(IllegalArgumentException.class, () -> ItineraryResearch.validateDestinationTime(
                request(LocalDate.now(honolulu).minusDays(1), LocalTime.of(10, 0),
                        LocalTime.of(14, 0)), honolulu.getId(), gaps));
    }
    @Test void daylightSavingWindowRemainsAnOpenQuestion() {
        var gaps = new ArrayList<String>();
        ItineraryResearch.validateDestinationTime(request(LocalDate.of(2027, 3, 14),
                LocalTime.of(1, 0), LocalTime.of(4, 0)), "America/New_York", gaps);
        assertEquals(1, gaps.size());
        assertTrue(gaps.getFirst().contains("daylight-saving"));
    }
    @Test void absentZoneDoesNotPretendClockIsVerified() {
        var gaps = new ArrayList<String>();
        ItineraryResearch.validateDestinationTime(request(LocalDate.now().plusDays(1),
                LocalTime.of(10, 0), LocalTime.of(14, 0)), null, gaps);
        assertFalse(gaps.isEmpty());
    }
}
