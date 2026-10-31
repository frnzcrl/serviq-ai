package com.serviq.platform.request.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "serviq_service_request")
public class ServiceRequestEntity {
    @Id
    @Column(name = "request_id", nullable = false)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "address_id", nullable = false)
    private UUID addressId;

    @Column(name = "asset_id")
    private UUID assetId;

    @Column(name = "issue_summary", nullable = false, length = 200)
    private String issueSummary;

    @Column(name = "issue_description", nullable = false, columnDefinition = "text")
    private String issueDescription;

    @Column(name = "priority", nullable = false, length = 20)
    private String priority;

    @Column(name = "request_status", nullable = false, length = 30)
    private String requestStatus;

    @Column(name = "intake_channel", nullable = false, length = 30)
    private String intakeChannel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected ServiceRequestEntity() {}

    public ServiceRequestEntity(UUID customerId, UUID addressId, UUID assetId,
                                String issueSummary, String issueDescription,
                                String priority, String intakeChannel) {
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.addressId = addressId;
        this.assetId = assetId;
        this.issueSummary = issueSummary;
        this.issueDescription = issueDescription;
        this.priority = priority;
        this.requestStatus = "NEW";
        this.intakeChannel = intakeChannel;
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        this.updatedAt = this.createdAt;
    }

    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public UUID getAddressId() { return addressId; }
    public UUID getAssetId() { return assetId; }
    public String getIssueSummary() { return issueSummary; }
    public String getIssueDescription() { return issueDescription; }
    public String getPriority() { return priority; }
    public String getRequestStatus() { return requestStatus; }
    public String getIntakeChannel() { return intakeChannel; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
