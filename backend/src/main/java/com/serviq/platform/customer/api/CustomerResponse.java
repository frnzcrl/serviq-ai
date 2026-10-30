package com.serviq.platform.customer.api;

import com.serviq.platform.customer.persistence.CustomerEntity;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CustomerResponse(
        UUID customerId,
        String fullName,
        String emailAddress,
        String phoneNumber,
        OffsetDateTime createdAt
) {

    public static CustomerResponse from(CustomerEntity customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getFullName(),
                customer.getEmailAddress(),
                customer.getPhoneNumber(),
                customer.getCreatedAt()
        );
    }
}
