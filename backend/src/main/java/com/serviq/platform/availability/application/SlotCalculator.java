package com.serviq.platform.availability.application;

import java.time.*;
import java.util.ArrayList;
import java.util.List;

/** Pure time-range logic. Intervals are half-open: [start, end). */
public final class SlotCalculator {
    private SlotCalculator() {}

    public record TimeRange(Instant start, Instant end) {
        public TimeRange {
            if (!start.isBefore(end)) throw new IllegalArgumentException("Invalid time range");
        }

        public boolean overlaps(Instant otherStart, Instant otherEnd) {
            return start.isBefore(otherEnd) && otherStart.isBefore(end);
        }
    }

    public static List<TimeRange> slots(LocalDate date, ZoneId zone,
                                        LocalTime workStart, LocalTime workEnd,
                                        Duration duration, Duration step,
                                        Instant now, List<TimeRange> unavailable) {
        if (!workStart.isBefore(workEnd)) throw new IllegalArgumentException("Invalid working hours");
        if (duration.isZero() || duration.isNegative() || step.isZero() || step.isNegative())
            throw new IllegalArgumentException("Duration and step must be positive");

        // Resolves time in technician's zone. Shift boundaries are local wall-clock times.
        ZonedDateTime windowStart = date.atTime(workStart).atZone(zone);
        ZonedDateTime windowEnd = date.atTime(workEnd).atZone(zone);
        List<TimeRange> result = new ArrayList<>();
        for (ZonedDateTime cursor = windowStart;
             !cursor.plus(duration).isAfter(windowEnd);
             cursor = cursor.plus(step)) {
            Instant from = cursor.toInstant();
            Instant until = cursor.plus(duration).toInstant();
            if (from.isBefore(now)) continue;
            if (unavailable.stream().noneMatch(range -> range.overlaps(from, until))) {
                result.add(new TimeRange(from, until));
            }
        }
        return result;
    }
}
