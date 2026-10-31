
package com.serviq.platform.availability.application;

import com.serviq.platform.availability.api.AvailabilitySlot;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
public class AvailabilityService {

    private final NamedParameterJdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    public AvailabilityService(NamedParameterJdbcTemplate jdbc) {
        this(jdbc, Clock.systemUTC());
    }

    AvailabilityService(
            NamedParameterJdbcTemplate jdbc,
            Clock clock
    ) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    private record Technician(
            UUID id,
            String name,
            ZoneId zone
    ) {}

    private record WorkingHours(
            UUID technicianId,
            LocalTime start,
            LocalTime end
    ) {}

    @Transactional(readOnly = true)
    public List<AvailabilitySlot> findSlots(
            String category,
            LocalDate date,
            int durationMinutes,
            int stepMinutes
    ) {

        LocalDate today = LocalDate.now(
                clock.withZone(ZoneOffset.UTC)
        );

        if (date.isBefore(today.minusDays(1))
                || date.isAfter(today.plusDays(60))) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Date must be within the next 60 days"
            );
        }

        if (category == null || category.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Asset category is required"
            );
        }

        if (durationMinutes < 15 || durationMinutes > 240
                || stepMinutes < 15 || stepMinutes > 60) {

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid duration or slot interval"
            );
        }

        String normalizedCategory = category.trim()
                .toUpperCase(Locale.ROOT);

        MapSqlParameterSource parameters =
                new MapSqlParameterSource(
                        "category",
                        normalizedCategory
                );

        // Find active technicians qualified for the category.
        List<Technician> eligible = jdbc.query(
                """
                SELECT
                    t.technician_id,
                    t.full_name,
                    t.time_zone
                FROM serviq_technician t
                JOIN serviq_technician_skill s
                    ON s.technician_id = t.technician_id
                WHERE t.active = true
                    AND s.asset_category = :category
                ORDER BY
                    t.full_name,
                    t.technician_id
                """,
                parameters,
                (rs, rowNum) -> new Technician(
                        rs.getObject(
                                "technician_id",
                                UUID.class
                        ),
                        rs.getString("full_name"),
                        ZoneId.of(
                                rs.getString("time_zone")
                        )
                )
        );

        if (eligible.isEmpty()) {
            return List.of();
        }

        List<UUID> technicianIds = eligible.stream()
                .map(Technician::id)
                .toList();

        parameters.addValue("ids", technicianIds);

        int dayOfWeek = date.getDayOfWeek().getValue();

        parameters.addValue("day", dayOfWeek);

        // Retrieve working shifts in one database query.
        List<WorkingHours> shifts = jdbc.query(
                """
                SELECT
                    technician_id,
                    start_time,
                    end_time
                FROM serviq_technician_working_hours
                WHERE technician_id IN (:ids)
                    AND day_of_week = :day
                ORDER BY
                    technician_id,
                    start_time
                """,
                parameters,
                (rs, rowNum) -> new WorkingHours(
                        rs.getObject(
                                "technician_id",
                                UUID.class
                        ),
                        rs.getTime(
                                "start_time"
                        ).toLocalTime(),
                        rs.getTime(
                                "end_time"
                        ).toLocalTime()
                )
        );

        if (shifts.isEmpty()) {
            return List.of();
        }

        // Include surrounding UTC days to cover local time zones.
        Instant windowStart = date.minusDays(1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();

        Instant windowEnd = date.plusDays(2)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant();

        parameters.addValue(
                "windowStart",
                Timestamp.from(windowStart)
        );

        parameters.addValue(
                "windowEnd",
                Timestamp.from(windowEnd)
        );

        Map<UUID, List<SlotCalculator.TimeRange>> unavailable =
                new HashMap<>();

        // Retrieve technician absences.
        jdbc.query(
                """
                SELECT
                    technician_id,
                    starts_at,
                    ends_at
                FROM serviq_technician_time_off
                WHERE technician_id IN (:ids)
                    AND starts_at < :windowEnd
                    AND ends_at > :windowStart
                """,
                parameters,
                (RowCallbackHandler) rs -> append(
                        unavailable,
                        rs.getObject(
                                "technician_id",
                                UUID.class
                        ),
                        rs.getTimestamp(
                                "starts_at"
                        ).toInstant(),
                        rs.getTimestamp(
                                "ends_at"
                        ).toInstant()
                )
        );

        // Retrieve existing active appointment assignments.
        jdbc.query(
                """
                SELECT
                    technician_id,
                    starts_at,
                    ends_at
                FROM serviq_appointment_technician
                WHERE technician_id IN (:ids)
                    AND active = true
                    AND starts_at < :windowEnd
                    AND ends_at > :windowStart
                """,
                parameters,
                (RowCallbackHandler) rs -> append(
                        unavailable,
                        rs.getObject(
                                "technician_id",
                                UUID.class
                        ),
                        rs.getTimestamp(
                                "starts_at"
                        ).toInstant(),
                        rs.getTimestamp(
                                "ends_at"
                        ).toInstant()
                )
        );

        // Group shifts by technician for efficient lookup.
        Map<UUID, List<WorkingHours>> shiftsByTechnician =
                new HashMap<>();

        for (WorkingHours shift : shifts) {
            shiftsByTechnician
                    .computeIfAbsent(
                            shift.technicianId(),
                            ignored -> new ArrayList<>()
                    )
                    .add(shift);
        }

        Duration duration = Duration.ofMinutes(
                durationMinutes
        );

        Duration step = Duration.ofMinutes(
                stepMinutes
        );

        Instant now = clock.instant();

        List<AvailabilitySlot> results = new ArrayList<>();

        // Calculate available slots per technician.
        for (Technician technician : eligible) {

            List<WorkingHours> technicianShifts =
                    shiftsByTechnician.getOrDefault(
                            technician.id(),
                            List.of()
                    );

            List<SlotCalculator.TimeRange> blockedRanges =
                    unavailable.getOrDefault(
                            technician.id(),
                            List.of()
                    );

            for (WorkingHours shift : technicianShifts) {

                List<SlotCalculator.TimeRange> availableSlots =
                        SlotCalculator.slots(
                                date,
                                technician.zone(),
                                shift.start(),
                                shift.end(),
                                duration,
                                step,
                                now,
                                blockedRanges
                        );

                for (SlotCalculator.TimeRange slot : availableSlots) {

                    results.add(
                            new AvailabilitySlot(
                                    technician.id(),
                                    technician.name(),
                                    slot.start().atOffset(
                                            ZoneOffset.UTC
                                    ),
                                    slot.end().atOffset(
                                            ZoneOffset.UTC
                                    )
                            )
                    );
                }
            }
        }

        results.sort(
                Comparator.comparing(
                        AvailabilitySlot::startsAt
                ).thenComparing(
                        AvailabilitySlot::technicianId
                )
        );

        return results;
    }

    private static void append(
            Map<UUID, List<SlotCalculator.TimeRange>> map,
            UUID technicianId,
            Instant start,
            Instant end
    ) {
        map.computeIfAbsent(
                technicianId,
                ignored -> new ArrayList<>()
        ).add(
                new SlotCalculator.TimeRange(
                        start,
                        end
                )
        );
    }
}
