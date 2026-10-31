package com.serviq.platform.technician.application;

import com.serviq.platform.technician.api.*;
import com.serviq.platform.technician.persistence.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

@Service
public class TechnicianService {
    private final TechnicianRepository technicians;
    private final WorkingHoursRepository workingHours;

    public TechnicianService(TechnicianRepository technicians, WorkingHoursRepository workingHours) {
        this.technicians = technicians;
        this.workingHours = workingHours;
    }

    @Transactional
    public TechnicianResponse register(RegisterTechnicianRequest request) {
        try {
            ZoneId.of(request.timeZone());
        } catch (DateTimeException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid IANA time zone", exception);
        }
        TechnicianEntity technician = new TechnicianEntity(
                request.fullName().trim(), normalize(request.emailAddress()), normalize(request.phoneNumber()),
                request.timeZone().trim());
        return TechnicianResponse.from(technicians.save(technician));
    }

    @Transactional(readOnly = true)
    public TechnicianResponse find(UUID technicianId) {
        return TechnicianResponse.from(requireTechnician(technicianId));
    }

    @Transactional(readOnly = true)
    public List<WorkingHoursResponse> listWorkingHours(UUID technicianId) {
        requireTechnician(technicianId);
        return workingHours.findByTechnicianIdOrderByDayOfWeekAscStartTimeAsc(technicianId)
                .stream().map(WorkingHoursResponse::from).toList();
    }

    @Transactional
    public WorkingHoursResponse addWorkingHours(UUID technicianId, AddWorkingHoursRequest request) {
        TechnicianEntity technician = requireTechnician(technicianId);
        if (!technician.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Technician is inactive");
        }
        if (!request.startTime().isBefore(request.endTime())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start time must precede end time");
        }
        // Serialize updates for one technician. Concurrency-safe while all schedule writers use this lock.
        technicians.findByIdForUpdate(technicianId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Technician not found"));
        boolean overlaps = workingHours.findByTechnicianIdOrderByDayOfWeekAscStartTimeAsc(technicianId)
                .stream().anyMatch(existing -> existing.getDayOfWeek() == request.dayOfWeek()
                        && existing.getStartTime().isBefore(request.endTime())
                        && request.startTime().isBefore(existing.getEndTime()));
        if (overlaps) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Working hours overlap an existing period");
        }
        return WorkingHoursResponse.from(workingHours.save(new WorkingHoursEntity(
                technicianId, request.dayOfWeek().shortValue(), request.startTime(), request.endTime())));
    }

    private TechnicianEntity requireTechnician(UUID technicianId) {
        return technicians.findById(technicianId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Technician not found"));
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
