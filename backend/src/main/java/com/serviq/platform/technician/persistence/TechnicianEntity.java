package com.serviq.platform.technician.persistence;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "serviq_technician")
public class TechnicianEntity {
    @Id
    @Column(name = "technician_id", nullable = false)
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "email_address", length = 255)
    private String emailAddress;

    @Column(name = "phone_number", length = 30)
    private String phoneNumber;

    @Column(name = "time_zone", nullable = false, length = 80)
    private String timeZone;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected TechnicianEntity() {}

    public TechnicianEntity(String fullName, String emailAddress, String phoneNumber, String timeZone) {
        this.id = UUID.randomUUID();
        this.fullName = fullName;
        this.emailAddress = emailAddress;
        this.phoneNumber = phoneNumber;
        this.timeZone = timeZone;
        this.active = true;
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmailAddress() { return emailAddress; }
    public String getPhoneNumber() { return phoneNumber; }
    public String getTimeZone() { return timeZone; }
    public boolean isActive() { return active; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
