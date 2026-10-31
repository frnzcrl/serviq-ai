package com.serviq.platform.availability.api;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AvailabilitySlot(UUID technicianId, String technicianName,
                               OffsetDateTime startsAt, OffsetDateTime endsAt) {}
