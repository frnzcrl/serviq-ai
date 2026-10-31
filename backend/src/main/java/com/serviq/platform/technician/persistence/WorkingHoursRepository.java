package com.serviq.platform.technician.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface WorkingHoursRepository extends JpaRepository<WorkingHoursEntity, UUID> {
    List<WorkingHoursEntity> findByTechnicianIdOrderByDayOfWeekAscStartTimeAsc(UUID technicianId);
}
