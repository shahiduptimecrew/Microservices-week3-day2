package com.example.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Body of {@code POST /api/v1/payments}.
 * <p>
 * {@code operationId} identifies the business operation (Order Service sends {@code order-<uuid>}, stored on the order).
 * Sending the same operationId again never charges twice: the stored payment is returned.
 */
public record PaymentRequest(

        @NotBlank
        @Size(max = 100)
        @Schema(example = "demo-operation-1")
        String operationId,

        @NotNull
        @Positive
        @Schema(example = "1")
        Long orderId,

        @NotNull
        @DecimalMin("0.01")
        @Digits(integer = 10, fraction = 2)
        @Schema(example = "10.00")
        BigDecimal amount,

        /* Optional, defaults to EUR. */
        @Pattern(regexp = "^[A-Z]{3}$", message = "must be a 3-letter ISO currency code")
        @Schema(example = "EUR")
        String currency
) {

    public String currencyOrDefault() {
        return currency == null ? "EUR" : currency;
    }
}
