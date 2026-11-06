package com.example.product.dto.response;

import java.math.BigDecimal;

/**
 * Answer to "can I buy {@code requestedQuantity} of this product, and at what price?".
 * Used by Order Service before it creates an order.
 * <p>
 * "Not enough stock" is a normal answer here ({@code available = false}, 200 OK),
 * not an error: the request itself was valid.
 */
public record AvailabilityResponse(
        Long productId,
        int requestedQuantity,
        int availableQuantity,
        boolean available,
        BigDecimal unitPrice
) {
}
