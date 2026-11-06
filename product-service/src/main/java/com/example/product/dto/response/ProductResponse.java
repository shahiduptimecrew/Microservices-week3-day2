package com.example.product.dto.response;

import java.math.BigDecimal;
import java.time.Instant;

/** v1 representation of a product. */
public record ProductResponse(
        Long id,
        String sku,
        String name,
        String description,
        BigDecimal price,
        int stockQuantity,
        Instant createdAt,
        Instant updatedAt
) {
}
