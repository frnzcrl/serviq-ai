package com.serviq.platform.technician.persistence;

import jakarta.persistence.*;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "serviq_technician_working_hours")
public class WorkingHoursEntity {
    @Id
    @Column(name = "working_hours_id", nullable = false)
    private UUID id;

    @Column(name = "technician_id", nullable = false)
    private UUID technicianId;

    @Column(name = "day_of_week", nullable = false)
    private short dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    protected WorkingHoursEntity() {}

    public WorkingHoursEntity(UUID technicianId, short dayOfWeek, LocalTime startTime, LocalTime endTime) {
        this.id = UUID.randomUUID();
        this.technicianId = technicianId;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public UUID getId() { return id; }
    public UUID getTechnicianId() { return technicianId; }
    public short getDayOfWeek() { return dayOfWeek; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
}
