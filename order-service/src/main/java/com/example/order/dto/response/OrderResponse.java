package com.example.order.dto.response;

import com.example.order.entity.OrderStatus;
import com.example.order.entity.PaymentStatus;
import org.springframework.hateoas.server.core.Relation;

import java.math.BigDecimal;
import java.time.Instant;

/** Order representation. {@code @Relation} names the HAL collection {@code _embedded.orders}. */
@Relation(itemRelation = "order", collectionRelation = "orders")
public record OrderResponse(
        Long id,
        Long productId,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal totalAmount,
        String currency,
        OrderStatus status,
        PaymentStatus paymentStatus,
        Long paymentId,
        String paymentOperationId,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
) {
}
