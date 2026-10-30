package com.serviq.platform.asset.api;

import com.serviq.platform.asset.persistence.AssetEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AssetResponse(
        UUID assetId, UUID customerId, String category, String manufacturer,
        String modelNumber, String serialNumber, String notes, OffsetDateTime createdAt
) {
    public static AssetResponse from(AssetEntity asset) {
        return new AssetResponse(asset.getId(), asset.getCustomerId(), asset.getCategory(),
                asset.getManufacturer(), asset.getModelNumber(), asset.getSerialNumber(),
                asset.getNotes(), asset.getCreatedAt());
    }
}
