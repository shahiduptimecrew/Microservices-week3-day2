package com.example.order.controller;

import com.example.order.dto.response.OrderResponse;
import com.example.order.entity.OrderStatus;
import com.example.order.entity.PaymentStatus;
import com.example.order.exception.OrderExceptions.DownstreamServiceException;
import com.example.order.exception.OrderExceptions.IdempotencyKeyMismatchException;
import com.example.order.exception.OrderExceptions.InsufficientStockException;
import com.example.order.exception.OrderExceptions.OrderNotFoundException;
import com.example.order.service.OrderService;
import com.example.order.service.OrderService.CreateOrderResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OrderController.class, properties = "eureka.client.enabled=false")
@Import(OrderModelAssembler.class)
class OrderControllerTest {

    private static final String BODY = "{\"productId\":1,\"quantity\":2}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OrderService orderService;

    private static OrderResponse order(OrderStatus status, PaymentStatus paymentStatus, Long paymentId) {
        Instant now = Instant.parse("2026-11-05T10:00:00Z");
        return new OrderResponse(10L, 1L, 2, new BigDecimal("24.99"), new BigDecimal("49.98"), "EUR",
                status, paymentStatus, paymentId, "order-6f1c", null, now, now);
    }

    @Test
    void create_returns201_withLocation_andHalLinks() throws Exception {
        when(orderService.create(eq("k1"), any()))
                .thenReturn(new CreateOrderResult(order(OrderStatus.CONFIRMED, PaymentStatus.SUCCESS, 7L), false));

        mockMvc.perform(post("/api/v1/orders").header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/orders/10"))
                .andExpect(header().string("Idempotent-Replayed", "false"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$._links.self.href").value("http://localhost/api/v1/orders/10"))
                .andExpect(jsonPath("$._links.product.href").value("http://localhost/api/v1/products/1"))
                .andExpect(jsonPath("$._links.payment.href").value("http://localhost/api/v1/payments/7"))
                .andExpect(jsonPath("$._links.retry-payment").doesNotExist());
    }

    @Test
    void replay_isMarkedWithHeader() throws Exception {
        when(orderService.create(eq("k1"), any()))
                .thenReturn(new CreateOrderResult(order(OrderStatus.CONFIRMED, PaymentStatus.SUCCESS, 7L), true));

        mockMvc.perform(post("/api/v1/orders").header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replayed", "true"));
    }

    @Test
    void pendingOrder_offersRetryPaymentLink() throws Exception {
        when(orderService.findById(10L)).thenReturn(order(OrderStatus.PENDING, PaymentStatus.PENDING, null));

        mockMvc.perform(get("/api/v1/orders/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.retry-payment.href", endsWith("/api/v1/orders/10/retry-payment")))
                .andExpect(jsonPath("$._links.payment").doesNotExist());
    }

    @Test
    void missingIdempotencyKey_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/orders").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(orderService);
    }

    @Test
    void invalidBody_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/orders").header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"productId\":-1,\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(2));
    }

    @Test
    void keyReusedWithOtherBody_returns409() throws Exception {
        when(orderService.create(eq("k1"), any())).thenThrow(new IdempotencyKeyMismatchException("k1"));

        mockMvc.perform(post("/api/v1/orders").header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://example.com/problems/idempotency-key-mismatch"));
    }

    @Test
    void outOfStock_returns409_withDetails() throws Exception {
        when(orderService.create(eq("k1"), any())).thenThrow(new InsufficientStockException(1L, 2, 1));

        mockMvc.perform(post("/api/v1/orders").header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.available").value(1));
    }

    @Test
    void productServiceTimeout_returns504() throws Exception {
        when(orderService.create(eq("k1"), any())).thenThrow(new DownstreamServiceException(
                "product-service", HttpStatus.GATEWAY_TIMEOUT, "Product Service did not answer in time", null));

        mockMvc.perform(post("/api/v1/orders").header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.service").value("product-service"));
    }

    @Test
    void unknownOrder_returns404() throws Exception {
        when(orderService.findById(99L)).thenThrow(new OrderNotFoundException(99L));

        mockMvc.perform(get("/api/v1/orders/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void list_returnsHalPage() throws Exception {
        when(orderService.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(
                List.of(order(OrderStatus.CONFIRMED, PaymentStatus.SUCCESS, 7L)), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.orders[0].id").value(10))
                .andExpect(jsonPath("$._embedded.orders[0]._links.self.href").exists())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$._links.self.href").exists());
    }
}
