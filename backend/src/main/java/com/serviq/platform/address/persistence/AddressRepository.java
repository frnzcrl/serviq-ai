package com.serviq.platform.address.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AddressRepository extends JpaRepository<AddressEntity, UUID> {
    Page<AddressEntity> findByCustomerId(UUID customerId, Pageable pageable);
    Optional<AddressEntity> findByIdAndCustomerId(UUID id, UUID customerId);
}
