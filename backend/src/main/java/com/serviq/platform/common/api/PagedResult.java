package com.serviq.platform.common.api;

import org.springframework.data.domain.Page;
import java.util.List;

public record PagedResult<T>(
        List<T> items, int page, int size, long totalItems, int totalPages
) {
    public static <T> PagedResult<T> from(Page<T> result) {
        return new PagedResult<>(result.getContent(), result.getNumber(),
                result.getSize(), result.getTotalElements(), result.getTotalPages());
    }
}
