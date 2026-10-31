package com.serviq.platform.request.application;

import com.serviq.platform.address.persistence.AddressRepository;
import com.serviq.platform.asset.persistence.AssetRepository;
import com.serviq.platform.customer.persistence.CustomerRepository;
import com.serviq.platform.request.api.CreateServiceRequest;
import com.serviq.platform.request.api.ServiceRequestResponse;
import com.serviq.platform.request.persistence.ServiceRequestEntity;
import com.serviq.platform.request.persistence.ServiceRequestRepository;
import com.serviq.platform.common.api.PagedResult;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ServiceRequestService {
    private static final Set<String> PRIORITIES = Set.of("LOW", "NORMAL", "HIGH", "URGENT");
    private static final Set<String> CHANNELS = Set.of("STAFF", "CUSTOMER", "AI_ASSISTED");

    private final CustomerRepository customers;
    private final AddressRepository addresses;
    private final AssetRepository assets;
    private final ServiceRequestRepository requests;

    public ServiceRequestService(CustomerRepository customers, AddressRepository addresses,
                                 AssetRepository assets, ServiceRequestRepository requests) {
        this.customers = customers;
        this.addresses = addresses;
        this.assets = assets;
        this.requests = requests;
    }

    @Transactional
    public ServiceRequestResponse create(CreateServiceRequest input) {
        if (!customers.existsById(input.customerId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found");
        }
        if (addresses.findByIdAndCustomerId(input.addressId(), input.customerId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Service address does not belong to customer");
        }
        if (input.assetId() != null &&
                assets.findByIdAndCustomerId(input.assetId(), input.customerId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Service asset does not belong to customer");
        }

        String summary = input.issueSummary().trim();
        String description = input.issueDescription().trim();
        if (summary.isEmpty() || description.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Issue summary and description must not be blank");
        }
        String priority = allowed(input.priority(), "NORMAL", PRIORITIES, "priority");
        String channel = allowed(input.intakeChannel(), "STAFF", CHANNELS, "intakeChannel");

        ServiceRequestEntity request = new ServiceRequestEntity(input.customerId(),
                input.addressId(), input.assetId(), summary, description, priority, channel);
        return ServiceRequestResponse.from(requests.saveAndFlush(request));
    }

    @Transactional(readOnly = true)
    public ServiceRequestResponse get(UUID requestId) {
        return requests.findById(requestId).map(ServiceRequestResponse::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Service request not found"));
    }

    @Transactional(readOnly = true)
    public PagedResult<ServiceRequestResponse> listForCustomer(UUID customerId, int page, int size) {
        if (!customers.existsById(customerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found");
        }
        return PagedResult.from(requests.findByCustomerId(customerId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")
                        .and(Sort.by(Sort.Direction.DESC, "id"))))
                .map(ServiceRequestResponse::from));
    }

    private static String allowed(String raw, String defaultValue,
                                  Set<String> values, String field) {
        String normalized = raw == null ? defaultValue : raw.trim().toUpperCase(Locale.ROOT);
        if (!values.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid " + field + ": " + normalized);
        }
        return normalized;
    }
}
