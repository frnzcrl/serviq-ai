package com.serviq.platform.operations;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationsService {
    private final JdbcTemplate jdbc;

    public OperationsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record UpcomingAppointment(UUID appointmentId, UUID requestId,
            OffsetDateTime startsAt, OffsetDateTime endsAt, String status) {}

    public record Summary(long totalCustomers, long openRequests,
            long activeAppointments, long activeTechnicians, long inProgressWorkOrders,
            long completedWorkOrders, List<UpcomingAppointment> upcomingAppointments,
            OffsetDateTime generatedAt) {}

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    @Transactional(readOnly = true)
    public Summary summary() {
        long customers = count("SELECT COUNT(*) FROM serviq_customer");
        long requests = count("SELECT COUNT(*) FROM serviq_service_request WHERE request_status NOT IN ('RESOLVED', 'CANCELLED')");
        long appointments = count("SELECT COUNT(*) FROM serviq_appointment WHERE appointment_status IN ('SCHEDULED', 'CONFIRMED', 'IN_PROGRESS') AND ends_at > CURRENT_TIMESTAMP");
        long technicians = count("SELECT COUNT(*) FROM serviq_technician WHERE active = TRUE");
        long inProgress = count("SELECT COUNT(*) FROM serviq_work_order WHERE work_order_status IN ('OPEN', 'IN_PROGRESS', 'ON_HOLD')");
        long completed = count("SELECT COUNT(*) FROM serviq_work_order WHERE work_order_status = 'COMPLETED'");

        List<UpcomingAppointment> upcoming = jdbc.query("""
                SELECT appointment_id, request_id, starts_at, ends_at, appointment_status
                FROM serviq_appointment
                WHERE appointment_status IN ('SCHEDULED', 'CONFIRMED', 'IN_PROGRESS')
                  AND ends_at > CURRENT_TIMESTAMP
                ORDER BY starts_at ASC, appointment_id ASC
                LIMIT 6
                """, (rs, row) -> new UpcomingAppointment(
                rs.getObject("appointment_id", UUID.class),
                rs.getObject("request_id", UUID.class),
                rs.getObject("starts_at", OffsetDateTime.class),
                rs.getObject("ends_at", OffsetDateTime.class),
                rs.getString("appointment_status")));
        return new Summary(customers, requests, appointments, technicians,
                inProgress, completed, upcoming, OffsetDateTime.now());
    }
}
