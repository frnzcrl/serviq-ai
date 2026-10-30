package com.serviq.platform.asset.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AssetRepository extends JpaRepository<AssetEntity, UUID> {
    Page<AssetEntity> findByCustomerId(UUID customerId, Pageable pageable);
    Optional<AssetEntity> findByIdAndCustomerId(UUID id, UUID customerId);
}
