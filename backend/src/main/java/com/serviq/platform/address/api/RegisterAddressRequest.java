package com.serviq.platform.address.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterAddressRequest(
        @Size(max = 80) String addressLabel,
        @NotBlank @Size(max = 255) String addressLine1,
        @Size(max = 255) String addressLine2,
        @NotBlank @Size(max = 120) String city,
        @Size(max = 120) String region,
        @Size(max = 30) String postalCode,
        @Pattern(regexp = "[A-Z]{2}") String countryCode
) {}
