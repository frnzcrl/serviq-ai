package com.serviq.platform.request.api;

import com.serviq.platform.common.api.PagedResult;
import com.serviq.platform.request.application.ServiceRequestService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.net.URI;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/api/v1/service-requests")
public class ServiceRequestController {
    private final ServiceRequestService service;

    public ServiceRequestController(ServiceRequestService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ServiceRequestResponse> create(
            @Valid @RequestBody CreateServiceRequest input) {
        ServiceRequestResponse saved = service.create(input);
        return ResponseEntity.created(URI.create("/api/v1/service-requests/" + saved.requestId()))
                .body(saved);
    }

    @GetMapping("/{requestId}")
    public ServiceRequestResponse get(@PathVariable UUID requestId) {
        return service.get(requestId);
    }

    @GetMapping("/customer/{customerId}")
    public PagedResult<ServiceRequestResponse> listForCustomer(
            @PathVariable UUID customerId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.listForCustomer(customerId, page, size);
    }
}
