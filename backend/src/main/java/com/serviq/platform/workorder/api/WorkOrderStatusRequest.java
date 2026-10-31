package com.serviq.platform.workorder.api;
import jakarta.validation.constraints.NotBlank;
public record WorkOrderStatusRequest(@NotBlank String status, String reason, @NotBlank String changedBy) {}
