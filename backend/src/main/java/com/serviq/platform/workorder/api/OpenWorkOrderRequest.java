package com.serviq.platform.workorder.api;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
public record OpenWorkOrderRequest(@NotNull UUID requestId, UUID appointmentId) {}
