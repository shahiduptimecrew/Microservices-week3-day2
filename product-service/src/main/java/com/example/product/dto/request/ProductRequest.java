package com.example.product.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Body for POST (create) and PUT (full replace).
 * <p>
 * PUT replaces the whole resource, so every required field must be sent again.
 * Validation runs before the controller method is called; a failure gives
 * 400 Bad Request as {@code application/problem+json} with one entry per invalid field.
 */
public record ProductRequest(

        @NotBlank
        @Size(max = 40)
        @Pattern(regexp = "^[A-Z0-9-]+$", message = "must contain only upper-case letters, digits and '-'")
        @Schema(example = "TABLET-10")
        String sku,

        @NotBlank
        @Size(max = 120)
        @Schema(example = "Tablet 10 inch")
        String name,

        @Size(max = 1000)
        @Schema(example = "10-inch tablet, 128 GB")
        String description,

        @NotNull
        @DecimalMin(value = "0.01")
        @Digits(integer = 10, fraction = 2)
        @Schema(example = "299.00")
        BigDecimal price,

        @NotNull
        @PositiveOrZero
        @Schema(example = "25")
        Integer stockQuantity
) {
}
