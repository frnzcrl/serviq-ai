package com.serviq.platform.appointment.api;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AppointmentResponse(
        UUID appointmentId,
        UUID requestId,
        UUID technicianId,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt,
        String appointmentStatus,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {}
