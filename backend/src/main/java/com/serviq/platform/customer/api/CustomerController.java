package com.serviq.platform.customer.api;

import com.serviq.platform.customer.application.CustomerService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    public ResponseEntity<CustomerResponse> registerCustomer(
            @Valid @RequestBody CustomerRegistrationRequest request
    ) {
        CustomerResponse customer = customerService.registerCustomer(request);

        return ResponseEntity
                .created(URI.create(
                        "/api/v1/customers/" + customer.customerId()
                ))
                .body(customer);
    }

    @GetMapping("/{customerId}")
    public CustomerResponse getCustomer(
            @PathVariable UUID customerId
    ) {
        return customerService.findCustomer(customerId);
    }
}
