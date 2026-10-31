package com.serviq.platform.technician.api;

import jakarta.validation.constraints.*;

public record RegisterTechnicianRequest(
    @NotBlank @Size(max = 150) String fullName,
    @Email @Size(max = 255) String emailAddress,
    @Size(max = 30) String phoneNumber,
    @NotBlank @Size(max = 80) String timeZone
) {}
