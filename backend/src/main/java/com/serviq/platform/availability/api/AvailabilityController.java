package com.serviq.platform.availability.api;

import com.serviq.platform.availability.application.AvailabilityService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDate;
import java.util.List;

@RestController
@Validated
@RequestMapping("/api/v1/availability")
public class AvailabilityController {
    private final AvailabilityService availabilityService;

    public AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @GetMapping
    public List<AvailabilitySlot> findAvailableSlots(
            @RequestParam @NotBlank @Size(max = 60) String category,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "60") @Min(15) @Max(240) int durationMinutes,
            @RequestParam(defaultValue = "30") @Min(15) @Max(60) int stepMinutes) {
        return availabilityService.findSlots(category, date, durationMinutes, stepMinutes);
    }
}
