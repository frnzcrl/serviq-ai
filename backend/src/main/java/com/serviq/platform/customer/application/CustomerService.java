package com.serviq.platform.customer.application;

import com.serviq.platform.customer.api.CustomerRegistrationRequest;
import com.serviq.platform.customer.api.CustomerResponse;
import com.serviq.platform.customer.persistence.CustomerEntity;
import com.serviq.platform.customer.persistence.CustomerRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @Transactional
    public CustomerResponse registerCustomer(
            CustomerRegistrationRequest request
    ) {
        CustomerEntity customer = new CustomerEntity(
                request.fullName().trim(),
                normalize(request.emailAddress()),
                normalize(request.phoneNumber())
        );

        CustomerEntity savedCustomer = customerRepository.save(customer);

        return CustomerResponse.from(savedCustomer);
    }

    @Transactional(readOnly = true)
    public CustomerResponse findCustomer(UUID customerId) {
        return customerRepository.findById(customerId)
                .map(CustomerResponse::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Customer not found"
                ));
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
