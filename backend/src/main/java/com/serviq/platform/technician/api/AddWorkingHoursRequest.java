package com.serviq.platform.technician.api;

import jakarta.validation.constraints.*;
import java.time.LocalTime;

public record AddWorkingHoursRequest(
    @NotNull @Min(1) @Max(7) Short dayOfWeek,
    @NotNull LocalTime startTime,
    @NotNull LocalTime endTime
) {}
