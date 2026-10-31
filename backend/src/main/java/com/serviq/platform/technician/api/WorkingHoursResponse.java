package com.serviq.platform.technician.api;

import com.serviq.platform.technician.persistence.WorkingHoursEntity;
import java.time.LocalTime;
import java.util.UUID;

public record WorkingHoursResponse(UUID workingHoursId, UUID technicianId, short dayOfWeek,
                                   LocalTime startTime, LocalTime endTime) {
    public static WorkingHoursResponse from(WorkingHoursEntity row) {
        return new WorkingHoursResponse(row.getId(), row.getTechnicianId(), row.getDayOfWeek(),
                row.getStartTime(), row.getEndTime());
    }
}
