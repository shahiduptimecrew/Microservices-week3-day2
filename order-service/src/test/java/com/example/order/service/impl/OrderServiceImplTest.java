package com.example.order.service.impl;

import com.example.order.client.PaymentClient;
import com.example.order.client.PaymentOutcome;
import com.example.order.client.ProductClient;
import com.example.order.client.ProductClient.ProductAvailability;
import com.example.order.dto.request.CreateOrderRequest;
import com.example.order.entity.Order;
import com.example.order.entity.OrderStatus;
import com.example.order.entity.PaymentStatus;
import com.example.order.exception.OrderExceptions.InsufficientStockException;
import com.example.order.exception.OrderExceptions.OrderNotAwaitingPaymentException;
import com.example.order.mapper.OrderMapper;
import com.example.order.repository.OrderRepository;
import com.example.order.service.IdempotencyService;
import com.example.order.service.OrderService.CreateOrderResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock private OrderRepository orderRepository;
    @Mock private ProductClient productClient;
    @Mock private PaymentClient paymentClient;
    @Mock private IdempotencyService idempotencyService;

    private OrderServiceImpl service;

    private final CreateOrderRequest request = new CreateOrderRequest(1L, 2);

    @BeforeEach
    void setUp() {
        service = new OrderServiceImpl(orderRepository, productClient, paymentClient, idempotencyService, new OrderMapper());
    }

    /** The repository assigns id 100 on first save and returns the same instance afterwards. */
    private void repositoryAssignsIds() {
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order order = inv.getArgument(0);
            if (order.getId() == null) {
                ReflectionTestUtils.setField(order, "id", 100L);
            }
            return order;
        });
    }

    private void inStock() {
        when(idempotencyService.start(eq("k1"), anyString())).thenReturn(Optional.empty());
        when(productClient.checkAvailability(1L, 2))
                .thenReturn(new ProductAvailability(1L, 10, true, new BigDecimal("24.99")));
    }

    @Test
    void successfulPayment_confirmsOrder() {
        inStock();
        repositoryAssignsIds();
        when(paymentClient.pay(startsWith("order-"), eq(100L), eq(new BigDecimal("49.98")), eq("EUR")))
                .thenReturn(new PaymentOutcome.Paid(7L));

        CreateOrderResult result = service.create("k1", request);

        assertThat(result.replayed()).isFalse();
        assertThat(result.order().status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(result.order().paymentStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.order().paymentId()).isEqualTo(7L);
        assertThat(result.order().totalAmount()).isEqualByComparingTo("49.98");
        verify(idempotencyService).complete("k1", 100L);
    }

    @Test
    void declinedPayment_failsOrder() {
        inStock();
        repositoryAssignsIds();
        when(paymentClient.pay(anyString(), any(), any(), any()))
                .thenReturn(new PaymentOutcome.Declined(8L, "Card declined by provider"));

        CreateOrderResult result = service.create("k1", request);

        assertThat(result.order().status()).isEqualTo(OrderStatus.FAILED);
        assertThat(result.order().paymentStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.order().failureReason()).contains("declined");
    }

    @Test
    void unknownPaymentOutcome_leavesOrderPending_neverPaid() {
        inStock();
        repositoryAssignsIds();
        when(paymentClient.pay(anyString(), any(), any(), any()))
                .thenReturn(new PaymentOutcome.Unknown("Payment circuit breaker is OPEN"));

        CreateOrderResult result = service.create("k1", request);

        assertThat(result.order().status()).isEqualTo(OrderStatus.PENDING);
        assertThat(result.order().paymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(result.order().paymentId()).isNull();
    }

    @Test
    void insufficientStock_throws_andReleasesKey_withoutCreatingOrder() {
        when(idempotencyService.start(eq("k1"), anyString())).thenReturn(Optional.empty());
        when(productClient.checkAvailability(1L, 2))
                .thenReturn(new ProductAvailability(1L, 1, false, new BigDecimal("24.99")));

        assertThatThrownBy(() -> service.create("k1", request)).isInstanceOf(InsufficientStockException.class);
        verify(orderRepository, never()).save(any());
        verify(idempotencyService).release("k1");
        verifyNoInteractions(paymentClient);
    }

    @Test
    void replayedKey_returnsEarlierOrder_withoutCallingOtherServices() {
        Order existing = new Order(1L, 2, new BigDecimal("24.99"), "EUR");
        ReflectionTestUtils.setField(existing, "id", 55L);
        existing.markPaid(7L);
        when(idempotencyService.start(eq("k1"), anyString())).thenReturn(Optional.of(55L));
        when(orderRepository.findById(55L)).thenReturn(Optional.of(existing));

        CreateOrderResult result = service.create("k1", request);

        assertThat(result.replayed()).isTrue();
        assertThat(result.order().id()).isEqualTo(55L);
        verifyNoInteractions(productClient, paymentClient);
    }

    @Test
    void retryPayment_onPendingOrder_usesSameOperationId_andConfirms() {
        Order pending = new Order(1L, 2, new BigDecimal("24.99"), "EUR");
        ReflectionTestUtils.setField(pending, "id", 100L);
        pending.markPaymentPending("timeout");
        when(orderRepository.findById(100L)).thenReturn(Optional.of(pending));
        when(orderRepository.save(pending)).thenReturn(pending);
        // The retry must reuse the operationId generated when the order was created.
        when(paymentClient.pay(pending.getPaymentOperationId(), 100L, new BigDecimal("49.98"), "EUR"))
                .thenReturn(new PaymentOutcome.Paid(9L));

        assertThat(service.retryPayment(100L).status()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void retryPayment_onConfirmedOrder_isConflict() {
        Order confirmed = new Order(1L, 2, new BigDecimal("24.99"), "EUR");
        confirmed.markPaid(1L);
        when(orderRepository.findById(100L)).thenReturn(Optional.of(confirmed));

        assertThatThrownBy(() -> service.retryPayment(100L)).isInstanceOf(OrderNotAwaitingPaymentException.class);
        verifyNoInteractions(paymentClient);
    }
}
