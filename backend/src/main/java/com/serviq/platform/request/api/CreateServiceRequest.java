package com.serviq.platform.request.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateServiceRequest(
        @NotNull UUID customerId,
        @NotNull UUID addressId,
        UUID assetId,
        @NotBlank @Size(max = 200) String issueSummary,
        @NotBlank String issueDescription,
        String priority,
        String intakeChannel
) {}
