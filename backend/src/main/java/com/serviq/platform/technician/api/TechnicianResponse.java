package com.serviq.platform.technician.api;

import com.serviq.platform.technician.persistence.TechnicianEntity;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TechnicianResponse(UUID technicianId, String fullName, String emailAddress,
                                 String phoneNumber, String timeZone, boolean active, OffsetDateTime createdAt) {
    public static TechnicianResponse from(TechnicianEntity technician) {
        return new TechnicianResponse(technician.getId(), technician.getFullName(), technician.getEmailAddress(),
                technician.getPhoneNumber(), technician.getTimeZone(), technician.isActive(), technician.getCreatedAt());
    }
}
