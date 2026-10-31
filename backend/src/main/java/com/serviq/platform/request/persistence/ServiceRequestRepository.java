package com.serviq.platform.request.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ServiceRequestRepository extends JpaRepository<ServiceRequestEntity, UUID> {
    Page<ServiceRequestEntity> findByCustomerId(UUID customerId, Pageable pageable);
}
