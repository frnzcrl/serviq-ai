package com.serviq.platform.appointment.application;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class AppointmentServiceTest {
    private final Instant now = Instant.parse("2026-11-01T00:00:00Z");

    @Test void rejectsZeroLength() {
        Instant start = now.plus(Duration.ofHours(1));
        assertThrows(ResponseStatusException.class, () -> AppointmentService.validateWindow(start, start, now));
    }

    @Test void rejectsPastBooking() {
        Instant start = now.minus(Duration.ofMinutes(1));
        assertThrows(ResponseStatusException.class, () -> AppointmentService.validateWindow(start, start.plus(Duration.ofHours(1)), now));
    }

    @Test void rejectsLongBooking() {
        Instant start = now.plus(Duration.ofDays(1));
        assertThrows(ResponseStatusException.class, () -> AppointmentService.validateWindow(start, start.plus(Duration.ofHours(5)), now));
    }

    @Test void acceptsOneHourBooking() {
        Instant start = now.plus(Duration.ofDays(1));
        assertDoesNotThrow(() -> AppointmentService.validateWindow(start, start.plus(Duration.ofHours(1)), now));
    }
}
