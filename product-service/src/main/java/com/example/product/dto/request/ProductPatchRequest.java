package com.example.product.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Body for PATCH (partial update).
 * <p>
 * Every field is optional: {@code null} means "leave unchanged". The constraints only apply
 * to fields that are present (Bean Validation skips {@code null} for these annotations).
 * <p>
 * Teaching note: with this simple design a client cannot set {@code description} back to
 * {@code null}. JSON Merge Patch (RFC 7396) solves that; it is left out to keep the demo short.
 */
public record ProductPatchRequest(

        @Size(min = 1, max = 40)
        @Pattern(regexp = "^[A-Z0-9-]+$", message = "must contain only upper-case letters, digits and '-'")
        String sku,

        @Size(min = 1, max = 120)
        String name,

        @Size(max = 1000)
        String description,

        @DecimalMin(value = "0.01")
        @Digits(integer = 10, fraction = 2)
        @Schema(example = "249.00")
        BigDecimal price,

        @PositiveOrZero
        Integer stockQuantity
) {
}
