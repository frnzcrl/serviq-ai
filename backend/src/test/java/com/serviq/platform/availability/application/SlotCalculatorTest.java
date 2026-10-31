package com.serviq.platform.availability.application;

import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SlotCalculatorTest {
    private final LocalDate date = LocalDate.of(2030, 6, 3); // Monday
    private final ZoneId manila = ZoneId.of("Asia/Manila");
    private final Instant now = Instant.parse("2030-06-01T00:00:00Z");

    @Test
    void returnsSlotsInsideLocalWorkingHours() {
        var result = SlotCalculator.slots(date, manila, LocalTime.of(9,0), LocalTime.of(11,0),
                Duration.ofHours(1), Duration.ofMinutes(30), now, List.of());
        assertEquals(3, result.size());
        assertEquals(Instant.parse("2030-06-03T01:00:00Z"), result.get(0).start());
    }

    @Test
    void excludesOverlapsButAllowsBackToBackSlots() {
        var blocked = new SlotCalculator.TimeRange(
                Instant.parse("2030-06-03T02:00:00Z"),
                Instant.parse("2030-06-03T03:00:00Z"));
        var result = SlotCalculator.slots(date, manila, LocalTime.of(9,0), LocalTime.of(12,0),
                Duration.ofHours(1), Duration.ofHours(1), now, List.of(blocked));
        assertEquals(2, result.size());
        assertEquals(Instant.parse("2030-06-03T01:00:00Z"), result.get(0).start());
        assertEquals(Instant.parse("2030-06-03T03:00:00Z"), result.get(1).start());
    }

    @Test
    void excludesPastTimeAndDoesNotExceedShiftEnd() {
        var result = SlotCalculator.slots(date, manila, LocalTime.of(9,0), LocalTime.of(10,30),
                Duration.ofHours(1), Duration.ofMinutes(30), Instant.parse("2030-06-03T01:31:00Z"), List.of());
        assertTrue(result.isEmpty());
    }
}
