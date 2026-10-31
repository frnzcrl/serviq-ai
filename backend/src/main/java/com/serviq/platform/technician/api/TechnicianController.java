package com.serviq.platform.technician.api;

import com.serviq.platform.technician.application.TechnicianService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/technicians")
public class TechnicianController {
    private final TechnicianService service;

    public TechnicianController(TechnicianService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<TechnicianResponse> register(@Valid @RequestBody RegisterTechnicianRequest request) {
        TechnicianResponse technician = service.register(request);
        return ResponseEntity.created(URI.create("/api/v1/technicians/" + technician.technicianId()))
                .body(technician);
    }

    @GetMapping("/{technicianId}")
    public TechnicianResponse get(@PathVariable UUID technicianId) {
        return service.find(technicianId);
    }

    @PostMapping("/{technicianId}/working-hours")
    public ResponseEntity<WorkingHoursResponse> addWorkingHours(@PathVariable UUID technicianId,
            @Valid @RequestBody AddWorkingHoursRequest request) {
        WorkingHoursResponse hours = service.addWorkingHours(technicianId, request);
        return ResponseEntity.created(URI.create("/api/v1/technicians/" + technicianId + "/working-hours/" + hours.workingHoursId()))
                .body(hours);
    }

    @GetMapping("/{technicianId}/working-hours")
    public List<WorkingHoursResponse> listWorkingHours(@PathVariable UUID technicianId) {
        return service.listWorkingHours(technicianId);
    }
}
