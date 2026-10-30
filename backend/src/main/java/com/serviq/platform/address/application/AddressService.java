package com.serviq.platform.address.application;

import com.serviq.platform.address.api.AddressResponse;
import com.serviq.platform.address.api.RegisterAddressRequest;
import com.serviq.platform.address.persistence.AddressEntity;
import com.serviq.platform.address.persistence.AddressRepository;
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
public class AddressService {
    private final AddressRepository addressRepository;
    private final CustomerRepository customerRepository;

    public AddressService(AddressRepository addressRepository, CustomerRepository customerRepository) {
        this.addressRepository = addressRepository;
        this.customerRepository = customerRepository;
    }

    @Transactional
    public AddressResponse register(UUID customerId, RegisterAddressRequest request) {
        requireCustomer(customerId);
        String country = request.countryCode() == null ? "PH" : request.countryCode();
        AddressEntity entity = new AddressEntity(customerId, optional(request.addressLabel()),
                required(request.addressLine1()), optional(request.addressLine2()),
                required(request.city()), optional(request.region()),
                optional(request.postalCode()), country);
        return AddressResponse.from(addressRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public PagedResult<AddressResponse> list(UUID customerId, int page, int size) {
        requireCustomer(customerId);
        return PagedResult.from(addressRepository.findByCustomerId(customerId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")
                        .and(Sort.by(Sort.Direction.DESC, "id"))))
                .map(AddressResponse::from));
    }

    @Transactional(readOnly = true)
    public AddressResponse get(UUID customerId, UUID addressId) {
        return addressRepository.findByIdAndCustomerId(addressId, customerId)
                .map(AddressResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Address not found"));
    }

    private void requireCustomer(UUID customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found");
        }
    }

    private static String required(String text) { return text.trim(); }
    private static String optional(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
