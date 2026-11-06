package com.example.order.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Body of {@code POST /api/v1/orders}. Price is not sent by the client: Product Service is the source of truth. */
public record CreateOrderRequest(

        @NotNull
        @Positive
        @Schema(example = "2")
        Long productId,

        @NotNull
        @Min(1)
        @Max(1000)
        @Schema(example = "1")
        Integer quantity
) {

    /** Canonical form used for the idempotency payload hash (independent of JSON formatting). */
    public String canonical() {
        return "productId=" + productId + ";quantity=" + quantity;
    }
}
