package com.serviq.platform.appointment.api;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.UUID;

public record BookAppointmentRequest(
        @NotNull UUID requestId,
        @NotNull UUID technicianId,
        @NotNull OffsetDateTime startsAt,
        @NotNull OffsetDateTime endsAt
) {}
