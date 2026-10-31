package com.serviq.platform.workorder.api;
import jakarta.validation.constraints.NotBlank;
public record WorkOrderNoteRequest(@NotBlank String text, @NotBlank String createdBy) {}
