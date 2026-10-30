package com.serviq.platform.asset.application;

import com.serviq.platform.asset.api.AssetResponse;
import com.serviq.platform.asset.api.RegisterAssetRequest;
import com.serviq.platform.asset.persistence.AssetEntity;
import com.serviq.platform.asset.persistence.AssetRepository;
import com.serviq.platform.customer.persistence.CustomerRepository;
import com.serviq.platform.common.api.PagedResult;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class AssetService {
    private final AssetRepository assetRepository;
    private final CustomerRepository customerRepository;

    public AssetService(AssetRepository assetRepository, CustomerRepository customerRepository) {
        this.assetRepository = assetRepository;
        this.customerRepository = customerRepository;
    }

    @Transactional
    public AssetResponse register(UUID customerId, RegisterAssetRequest request) {
        requireCustomer(customerId);
        AssetEntity entity = new AssetEntity(customerId, request.category().trim(),
                optional(request.manufacturer()), optional(request.modelNumber()),
                optional(request.serialNumber()), optional(request.notes()));
        return AssetResponse.from(assetRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public PagedResult<AssetResponse> list(UUID customerId, int page, int size) {
        requireCustomer(customerId);
        return PagedResult.from(assetRepository.findByCustomerId(customerId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")
                        .and(Sort.by(Sort.Direction.DESC, "id"))))
                .map(AssetResponse::from));
    }

    @Transactional(readOnly = true)
    public AssetResponse get(UUID customerId, UUID assetId) {
        return assetRepository.findByIdAndCustomerId(assetId, customerId)
                .map(AssetResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asset not found"));
    }

    private void requireCustomer(UUID customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found");
        }
    }

    private static String optional(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
