package com.serviq.platform.asset.api;

import com.serviq.platform.asset.application.AssetService;
import jakarta.validation.Valid;
import com.serviq.platform.common.api.PagedResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers/{customerId}/assets")
public class AssetController {
    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @PostMapping
    public ResponseEntity<AssetResponse> register(@PathVariable UUID customerId,
                                                   @Valid @RequestBody RegisterAssetRequest request) {
        AssetResponse saved = assetService.register(customerId, request);
        URI location = URI.create("/api/v1/customers/" + customerId + "/assets/" + saved.assetId());
        return ResponseEntity.created(location).body(saved);
    }

    @GetMapping
    public PagedResult<AssetResponse> list(@PathVariable UUID customerId,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid pagination parameters");
        }
        return assetService.list(customerId, page, size);
    }

    @GetMapping("/{assetId}")
    public AssetResponse get(@PathVariable UUID customerId, @PathVariable UUID assetId) {
        return assetService.get(customerId, assetId);
    }
}
