package com.serviq.platform.address.api;

import com.serviq.platform.address.persistence.AddressEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AddressResponse(
        UUID addressId, UUID customerId, String addressLabel,
        String addressLine1, String addressLine2, String city,
        String region, String postalCode, String countryCode,
        OffsetDateTime createdAt
) {
    public static AddressResponse from(AddressEntity address) {
        return new AddressResponse(address.getId(), address.getCustomerId(),
                address.getAddressLabel(), address.getAddressLine1(),
                address.getAddressLine2(), address.getCity(), address.getRegion(),
                address.getPostalCode(), address.getCountryCode(), address.getCreatedAt());
    }
}
