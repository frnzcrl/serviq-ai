package com.serviq.platform.appointment.application;

import com.serviq.platform.appointment.api.AppointmentResponse;
import com.serviq.platform.appointment.api.BookAppointmentRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
public class AppointmentService {
    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    public AppointmentService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.clock = Clock.systemUTC();
    }

    private record Technician(ZoneId zone) {}
    private record RequestInfo(String status, String category) {}

    static void validateWindow(Instant start, Instant end, Instant now) {
        if (!start.isBefore(end)) bad("Appointment must end after it starts");
        long minutes = Duration.between(start, end).toMinutes();
        if (minutes < 15 || minutes > 240 || !start.plus(Duration.ofMinutes(minutes)).equals(end)) {
            bad("Appointment duration must be between 15 and 240 whole minutes");
        }
        if (!start.isAfter(now)) bad("Appointment must start in the future");
        if (start.isAfter(now.plus(Duration.ofDays(60)))) bad("Appointments can be booked up to 60 days ahead");
    }

    @Transactional
    public AppointmentResponse book(BookAppointmentRequest input) {
        Instant start = input.startsAt().toInstant();
        Instant end = input.endsAt().toInstant();
        validateWindow(start, end, clock.instant());

        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("requestId", input.requestId())
                .addValue("technicianId", input.technicianId())
                .addValue("start", Timestamp.from(start))
                .addValue("end", Timestamp.from(end));

        // The lock serializes booking attempts for this technician when made through this service.
        List<Technician> staff = jdbc.query("""
                SELECT time_zone FROM serviq_technician
                WHERE technician_id = :technicianId AND active = true FOR UPDATE
                """, p, (rs, row) -> new Technician(ZoneId.of(rs.getString("time_zone"))));
        if (staff.isEmpty()) unprocessable("Technician is not active or does not exist");
        ZoneId zone = staff.getFirst().zone();

        // Request and asset category must come from the database, not user-supplied claims.
        List<RequestInfo> requests = jdbc.query("""
                SELECT r.request_status, a.asset_category
                FROM serviq_service_request r
                LEFT JOIN serviq_service_asset a ON a.asset_id = r.asset_id
                WHERE r.request_id = :requestId
                FOR UPDATE OF r
                """, p, (rs, row) -> new RequestInfo(rs.getString("request_status"), rs.getString("asset_category")));
        if (requests.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Service request not found");
        RequestInfo request = requests.getFirst();
        if (Set.of("CANCELLED", "RESOLVED", "IN_PROGRESS").contains(request.status())) {
            unprocessable("Request cannot be booked in its current status");
        }
        if (request.category() == null) unprocessable("Service request must reference an asset with a category");
        p.addValue("category", request.category());

        if (!exists("""
                SELECT 1 FROM serviq_technician_skill
                WHERE technician_id = :technicianId AND asset_category = :category
                """, p)) unprocessable("Technician is not qualified for this asset category");

        // This version supports single-day, non-overnight working shifts only (same as V3).
        ZonedDateTime localStart = start.atZone(zone);
        ZonedDateTime localEnd = end.atZone(zone);
        if (!localStart.toLocalDate().equals(localEnd.toLocalDate())) {
            unprocessable("Appointment cannot cross a local calendar day");
        }
        p.addValue("day", localStart.getDayOfWeek().getValue())
                .addValue("localStart", java.sql.Time.valueOf(localStart.toLocalTime()))
                .addValue("localEnd", java.sql.Time.valueOf(localEnd.toLocalTime()));

        if (!exists("""
                SELECT 1 FROM serviq_technician_working_hours
                WHERE technician_id = :technicianId AND day_of_week = :day
                  AND start_time <= :localStart AND end_time >= :localEnd
                """, p)) unprocessable("Appointment falls outside technician working hours");

        if (exists("""
                SELECT 1 FROM serviq_technician_time_off
                WHERE technician_id = :technicianId
                  AND starts_at < :end AND ends_at > :start
                """, p)) conflict("Technician is unavailable during this period");

        if (exists("""
                SELECT 1 FROM serviq_appointment_technician
                WHERE technician_id = :technicianId AND active = true
                  AND starts_at < :end AND ends_at > :start
                """, p)) conflict("Technician already has a booking during this period");

        // One active appointment per service request in this MVP.
        if (exists("""
                SELECT 1 FROM serviq_appointment
                WHERE request_id = :requestId
                  AND appointment_status <> 'CANCELLED'
                """, p)) conflict("Service request already has an active appointment");

        UUID appointmentId = UUID.randomUUID();
        UUID assignmentId = UUID.randomUUID();
        p.addValue("appointmentId", appointmentId).addValue("assignmentId", assignmentId);
        jdbc.update("""
                INSERT INTO serviq_appointment
                    (appointment_id, request_id, starts_at, ends_at, appointment_status)
                VALUES (:appointmentId, :requestId, :start, :end, 'SCHEDULED')
                """, p);
        jdbc.update("""
                INSERT INTO serviq_appointment_technician
                    (assignment_id, appointment_id, technician_id, starts_at, ends_at, active)
                VALUES (:assignmentId, :appointmentId, :technicianId, :start, :end, true)
                """, p);
        jdbc.update("""
                UPDATE serviq_service_request SET request_status = 'SCHEDULED', updated_at = now()
                WHERE request_id = :requestId
                """, p);
        return get(appointmentId);
    }

    @Transactional(readOnly = true)
    public AppointmentResponse get(UUID id) {
        List<AppointmentResponse> found = jdbc.query("""
                SELECT a.appointment_id, a.request_id, at.technician_id,
                       a.starts_at, a.ends_at, a.appointment_status, a.created_at, a.updated_at
                FROM serviq_appointment a
                LEFT JOIN serviq_appointment_technician at ON at.appointment_id = a.appointment_id
                WHERE a.appointment_id = :id
                """, new MapSqlParameterSource("id", id), AppointmentService::map);
        if (found.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found");
        return found.getFirst();
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> list(LocalDate date) {
        Instant start = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("start", Timestamp.from(start)).addValue("end", Timestamp.from(end));
        return jdbc.query("""
                SELECT a.appointment_id, a.request_id, at.technician_id,
                       a.starts_at, a.ends_at, a.appointment_status, a.created_at, a.updated_at
                FROM serviq_appointment a
                LEFT JOIN serviq_appointment_technician at ON at.appointment_id = a.appointment_id
                WHERE a.starts_at < :end AND a.ends_at > :start
                ORDER BY a.starts_at, a.appointment_id
                """, p, AppointmentService::map);
    }

    @Transactional
    public AppointmentResponse cancel(UUID id) {
        MapSqlParameterSource p = new MapSqlParameterSource("id", id);
        List<UUID> requests = jdbc.query("""
                SELECT request_id FROM serviq_appointment
                WHERE appointment_id = :id FOR UPDATE
                """, p, (rs, row) -> rs.getObject("request_id", UUID.class));
        if (requests.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found");
        UUID requestId = requests.getFirst();
        p.addValue("requestId", requestId);
        jdbc.update("""
                UPDATE serviq_appointment SET appointment_status = 'CANCELLED', updated_at = now()
                WHERE appointment_id = :id AND appointment_status IN ('SCHEDULED', 'CONFIRMED')
                """, p);
        // Only release assignments when the appointment is actually cancelled.
        String status = jdbc.queryForObject("""
                SELECT appointment_status FROM serviq_appointment WHERE appointment_id = :id
                """, p, String.class);
        if (!"CANCELLED".equals(status)) conflict("Only scheduled or confirmed appointments can be cancelled");
        jdbc.update("""
                UPDATE serviq_appointment_technician SET active = false
                WHERE appointment_id = :id AND active = true
                """, p);
        jdbc.update("""
                UPDATE serviq_service_request SET request_status = 'TRIAGED', updated_at = now()
                WHERE request_id = :requestId AND request_status = 'SCHEDULED'
                  AND NOT EXISTS (
                    SELECT 1 FROM serviq_appointment
                    WHERE request_id = :requestId AND appointment_status <> 'CANCELLED'
                  )
                """, p);
        return get(id);
    }

    private boolean exists(String sql, MapSqlParameterSource p) {
        return !jdbc.query(sql, p, (rs, row) -> 1).isEmpty();
    }

    private static AppointmentResponse map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new AppointmentResponse(
                rs.getObject("appointment_id", UUID.class),
                rs.getObject("request_id", UUID.class),
                rs.getObject("technician_id", UUID.class),
                rs.getObject("starts_at", OffsetDateTime.class),
                rs.getObject("ends_at", OffsetDateTime.class),
                rs.getString("appointment_status"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class));
    }

    private static void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static void unprocessable(String message) { throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message); }
    private static void conflict(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
