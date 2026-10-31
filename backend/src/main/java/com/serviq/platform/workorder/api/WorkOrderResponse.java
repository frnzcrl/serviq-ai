package com.serviq.platform.workorder.api;
import java.time.OffsetDateTime;
import java.util.UUID;
public record WorkOrderResponse(UUID workOrderId, Long workOrderNumber, UUID requestId,
    String status, String diagnosis, String resolutionNotes, OffsetDateTime openedAt,
    OffsetDateTime startedAt, OffsetDateTime completedAt, OffsetDateTime updatedAt) {}
