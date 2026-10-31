package com.serviq.platform.request.application;

import com.serviq.platform.address.persistence.AddressRepository;
import com.serviq.platform.asset.persistence.AssetRepository;
import com.serviq.platform.customer.persistence.CustomerRepository;
import com.serviq.platform.request.api.CreateServiceRequest;
import com.serviq.platform.request.persistence.ServiceRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ServiceRequestServiceTest {
    @Mock CustomerRepository customers;
    @Mock AddressRepository addresses;
    @Mock AssetRepository assets;
    @Mock ServiceRequestRepository requests;
    ServiceRequestService service;

    @BeforeEach
    void setup() { service = new ServiceRequestService(customers, addresses, assets, requests); }

    @Test
    void rejectsUnknownCustomerBeforeCreatingRequest() {
        UUID customerId = UUID.randomUUID();
        var input = new CreateServiceRequest(customerId, UUID.randomUUID(), null,
                "Refrigerator not cooling", "Stopped working yesterday", null, null);
        when(customers.existsById(customerId)).thenReturn(false);
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.create(input));
        assertEquals(404, error.getStatusCode().value());
        verifyNoInteractions(requests);
    }

    @Test
    void rejectsAddressOwnedByDifferentCustomer() {
        UUID customerId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        var input = new CreateServiceRequest(customerId, addressId, null,
                "Refrigerator not cooling", "Stopped working yesterday", null, null);
        when(customers.existsById(customerId)).thenReturn(true);
        when(addresses.findByIdAndCustomerId(addressId, customerId)).thenReturn(Optional.empty());
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.create(input));
        assertEquals(422, error.getStatusCode().value());
        verifyNoInteractions(requests);
    }
}
