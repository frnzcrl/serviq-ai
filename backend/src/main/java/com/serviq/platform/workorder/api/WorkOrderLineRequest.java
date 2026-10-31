package com.serviq.platform.workorder.api;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record WorkOrderLineRequest(@NotBlank String type, @NotBlank String description,
    @NotNull @DecimalMin(value="0.001") BigDecimal quantity,
    @NotNull @DecimalMin(value="0.00") BigDecimal unitPrice,
    @NotBlank String currencyCode, String notes) {}
