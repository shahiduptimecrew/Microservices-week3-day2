package com.example.order.mapper;

import com.example.order.dto.response.OrderResponse;
import com.example.order.entity.Order;
import org.springframework.stereotype.Component;

@Component
public class OrderMapper {

    public OrderResponse toResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getProductId(),
                order.getQuantity(),
                order.getUnitPrice(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getStatus(),
                order.getPaymentStatus(),
                order.getPaymentId(),
                order.getPaymentOperationId(),
                order.getFailureReason(),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }
}
