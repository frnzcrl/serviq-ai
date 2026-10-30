package com.serviq.platform.asset.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterAssetRequest(
        @NotBlank @Size(max = 60) String category,
        @Size(max = 120) String manufacturer,
        @Size(max = 120) String modelNumber,
        @Size(max = 150) String serialNumber,
        @Size(max = 4000) String notes
) {}
