package com.serviq.platform.asset.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "serviq_service_asset")
public class AssetEntity {
    @Id
    @Column(name = "asset_id", nullable = false)
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "asset_category", nullable = false, length = 60)
    private String category;

    @Column(name = "manufacturer", length = 120)
    private String manufacturer;

    @Column(name = "model_number", length = 120)
    private String modelNumber;

    @Column(name = "serial_number", length = 150)
    private String serialNumber;

    @Column(name = "asset_notes", columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected AssetEntity() {}

    public AssetEntity(UUID customerId, String category, String manufacturer,
                       String modelNumber, String serialNumber, String notes) {
        this.id = UUID.randomUUID();
        this.customerId = customerId;
        this.category = category;
        this.manufacturer = manufacturer;
        this.modelNumber = modelNumber;
        this.serialNumber = serialNumber;
        this.notes = notes;
        this.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public UUID getId() { return id; }
    public UUID getCustomerId() { return customerId; }
    public String getCategory() { return category; }
    public String getManufacturer() { return manufacturer; }
    public String getModelNumber() { return modelNumber; }
    public String getSerialNumber() { return serialNumber; }
    public String getNotes() { return notes; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
