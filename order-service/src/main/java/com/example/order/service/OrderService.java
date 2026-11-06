package com.example.order.service;

import com.example.order.dto.request.CreateOrderRequest;
import com.example.order.dto.response.OrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService {

    /** @param replayed true if this is the stored result of an earlier request with the same Idempotency-Key */
    record CreateOrderResult(OrderResponse order, boolean replayed) {
    }

    CreateOrderResult create(String idempotencyKey, CreateOrderRequest request);

    Page<OrderResponse> findAll(Pageable pageable);

    OrderResponse findById(Long id);

    OrderResponse retryPayment(Long id);
}
