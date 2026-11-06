package com.example.order.service.impl;

import com.example.order.client.PaymentClient;
import com.example.order.client.PaymentOutcome;
import com.example.order.client.ProductClient;
import com.example.order.client.ProductClient.ProductAvailability;
import com.example.order.dto.request.CreateOrderRequest;
import com.example.order.dto.response.OrderResponse;
import com.example.order.entity.Order;
import com.example.order.entity.OrderStatus;
import com.example.order.exception.OrderExceptions.InsufficientStockException;
import com.example.order.exception.OrderExceptions.OrderNotAwaitingPaymentException;
import com.example.order.exception.OrderExceptions.OrderNotFoundException;
import com.example.order.mapper.OrderMapper;
import com.example.order.repository.OrderRepository;
import com.example.order.service.IdempotencyService;
import com.example.order.service.OrderService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Order workflow: idempotency check → stock check (Product) → save order → pay (Payment) → update order.
 * <p>
 * Teaching point: {@link #create} is deliberately <b>not</b> one big {@code @Transactional} method.
 * Holding a database transaction open across remote HTTP calls ties up a connection for seconds
 * and still cannot roll back the other services. Each database write is its own short transaction,
 * and every intermediate state (CREATED, PENDING) is a valid, visible state.
 * <p>
 * Simplification for the demo: stock is checked, not reserved. A real shop would reserve stock
 * (or use a saga) so two orders cannot both take the last unit.
 */
@Service
public class OrderServiceImpl implements OrderService {

    private static final String CURRENCY = "EUR";

    private final OrderRepository orderRepository;
    private final ProductClient productClient;
    private final PaymentClient paymentClient;
    private final IdempotencyService idempotencyService;
    private final OrderMapper mapper;

    public OrderServiceImpl(OrderRepository orderRepository, ProductClient productClient, PaymentClient paymentClient,
                            IdempotencyService idempotencyService, OrderMapper mapper) {
        this.orderRepository = orderRepository;
        this.productClient = productClient;
        this.paymentClient = paymentClient;
        this.idempotencyService = idempotencyService;
        this.mapper = mapper;
    }

    @Override
    public CreateOrderResult create(String idempotencyKey, CreateOrderRequest request) {
        String requestHash = IdempotencyService.hash(request.canonical());
        Optional<Long> earlierOrderId = idempotencyService.start(idempotencyKey, requestHash);
        if (earlierOrderId.isPresent()) {
            return new CreateOrderResult(findById(earlierOrderId.get()), true);
        }

        try {
            ProductAvailability availability = productClient.checkAvailability(request.productId(), request.quantity());
            if (!availability.available()) {
                throw new InsufficientStockException(request.productId(), request.quantity(),
                        availability.availableQuantity());
            }

            Order order = orderRepository.save(
                    new Order(request.productId(), request.quantity(), availability.unitPrice(), CURRENCY));
            order = pay(order);

            idempotencyService.complete(idempotencyKey, order.getId());
            return new CreateOrderResult(mapper.toResponse(order), false);
        } catch (RuntimeException e) {
            idempotencyService.release(idempotencyKey);
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> findAll(Pageable pageable) {
        return orderRepository.findAll(pageable).map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse findById(Long id) {
        return mapper.toResponse(load(id));
    }

    /**
     * Resolves a PENDING order. Safe to call any number of times: the order's stored operationId
     * is sent every time, so Payment Service charges at most once.
     */
    @Override
    public OrderResponse retryPayment(Long id) {
        Order order = load(id);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new OrderNotAwaitingPaymentException(id, order.getStatus());
        }
        return mapper.toResponse(pay(order));
    }

    private Order pay(Order order) {
        PaymentOutcome outcome = paymentClient.pay(
                order.getPaymentOperationId(), order.getId(), order.getTotalAmount(), order.getCurrency());
        switch (outcome) {
            case PaymentOutcome.Paid paid -> order.markPaid(paid.paymentId());
            case PaymentOutcome.Declined declined -> order.markPaymentFailed(declined.paymentId(), declined.reason());
            case PaymentOutcome.Rejected rejected -> order.markPaymentFailed(null, rejected.reason());
            case PaymentOutcome.Unknown unknown -> order.markPaymentPending(unknown.reason());
        }
        return orderRepository.save(order);
    }

    private Order load(Long id) {
        return orderRepository.findById(id).orElseThrow(() -> new OrderNotFoundException(id));
    }
}
