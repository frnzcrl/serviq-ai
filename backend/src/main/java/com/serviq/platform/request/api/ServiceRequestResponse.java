package com.serviq.platform.request.api;

import com.serviq.platform.request.persistence.ServiceRequestEntity;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ServiceRequestResponse(
        UUID requestId, UUID customerId, UUID addressId, UUID assetId,
        String issueSummary, String issueDescription,
        String priority, String requestStatus, String intakeChannel,
        OffsetDateTime createdAt, OffsetDateTime updatedAt
) {
    public static ServiceRequestResponse from(ServiceRequestEntity entity) {
        return new ServiceRequestResponse(entity.getId(), entity.getCustomerId(),
                entity.getAddressId(), entity.getAssetId(), entity.getIssueSummary(),
                entity.getIssueDescription(), entity.getPriority(), entity.getRequestStatus(),
                entity.getIntakeChannel(), entity.getCreatedAt(), entity.getUpdatedAt());
    }
}
