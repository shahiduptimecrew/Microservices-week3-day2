package com.example.product.dto.response;

import java.math.BigDecimal;

/**
 * v2 representation of a product (API versioning demo).
 * <p>
 * The shape changed in a way that would break v1 clients:
 * {@code price} became an object with a currency, and stock became an object.
 * That is why it lives under a new URI version ({@code /api/v2}) instead of
 * replacing the v1 response.
 */
public record ProductV2Response(
        Long id,
        String sku,
        String name,
        String description,
        Money price,
        Stock stock
) {

    public record Money(BigDecimal amount, String currency) {
    }

    public record Stock(int quantity, boolean inStock) {
    }
}
