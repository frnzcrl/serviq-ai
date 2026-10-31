package com.serviq.platform.appointment.api;

import com.serviq.platform.appointment.application.AppointmentService;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/appointments")
public class AppointmentController {
    private final AppointmentService service;

    public AppointmentController(AppointmentService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<AppointmentResponse> book(@Valid @RequestBody BookAppointmentRequest input) {
        AppointmentResponse saved = service.book(input);
        return ResponseEntity.created(URI.create("/api/v1/appointments/" + saved.appointmentId())).body(saved);
    }

    @GetMapping("/{appointmentId}")
    public AppointmentResponse get(@PathVariable UUID appointmentId) {
        return service.get(appointmentId);
    }

    @GetMapping
    public List<AppointmentResponse> list(@RequestParam LocalDate date) {
        return service.list(date);
    }

    @PatchMapping("/{appointmentId}/cancel")
    public AppointmentResponse cancel(@PathVariable UUID appointmentId) {
        return service.cancel(appointmentId);
    }

    // Called after the transactional service has rolled back on a database constraint violation.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<org.springframework.http.ProblemDetail> conflict(DataIntegrityViolationException ex) {
        org.springframework.http.ProblemDetail problem = org.springframework.http.ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "Booking conflicts with existing data or violates a scheduling constraint");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
    }
}
