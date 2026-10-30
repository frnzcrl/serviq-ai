package com.serviq.platform.address.api;

import com.serviq.platform.address.application.AddressService;
import jakarta.validation.Valid;
import com.serviq.platform.common.api.PagedResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers/{customerId}/addresses")
public class AddressController {
    private final AddressService addressService;

    public AddressController(AddressService addressService) {
        this.addressService = addressService;
    }

    @PostMapping
    public ResponseEntity<AddressResponse> register(@PathVariable UUID customerId,
                                                     @Valid @RequestBody RegisterAddressRequest request) {
        AddressResponse saved = addressService.register(customerId, request);
        URI location = URI.create("/api/v1/customers/" + customerId + "/addresses/" + saved.addressId());
        return ResponseEntity.created(location).body(saved);
    }

    @GetMapping
    public PagedResult<AddressResponse> list(@PathVariable UUID customerId,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, "Invalid pagination parameters");
        }
        return addressService.list(customerId, page, size);
    }

    @GetMapping("/{addressId}")
    public AddressResponse get(@PathVariable UUID customerId, @PathVariable UUID addressId) {
        return addressService.get(customerId, addressId);
    }
}
