package com.example.payment.controller;

import com.example.payment.dto.response.PaymentResponse;
import com.example.payment.entity.PaymentStatus;
import com.example.payment.exception.OperationIdConflictException;
import com.example.payment.exception.ProviderUnavailableException;
import com.example.payment.service.PaymentProviderSimulator;
import com.example.payment.service.PaymentService;
import com.example.payment.service.PaymentService.ProcessResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PaymentController.class, properties = "eureka.client.enabled=false")
@Import(PaymentProviderSimulator.class)
class PaymentControllerTest {

    private static final String BODY = """
            {"operationId":"order-1","orderId":1,"amount":50.00}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PaymentService paymentService;

    private static PaymentResponse payment() {
        return new PaymentResponse(10L, "order-1", 1L, new BigDecimal("50.00"), "EUR",
                PaymentStatus.SUCCESS, null, Instant.parse("2026-11-05T10:00:00Z"));
    }

    @Test
    void newPayment_returns201() throws Exception {
        when(paymentService.process(any())).thenReturn(new ProcessResult(payment(), true));

        mockMvc.perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/payments/10"))
                .andExpect(jsonPath("$.status").value("SUCCESS"));
    }

    @Test
    void duplicateOperationId_returns200_withSamePayment() throws Exception {
        when(paymentService.process(any())).thenReturn(new ProcessResult(payment(), false));

        mockMvc.perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10));
    }

    @Test
    void operationIdReusedWithOtherPayload_returns409() throws Exception {
        when(paymentService.process(any())).thenThrow(new OperationIdConflictException("order-1"));

        mockMvc.perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.operationId").value("order-1"));
    }

    @Test
    void providerDown_returns503_withRetryAfter() throws Exception {
        when(paymentService.process(any())).thenThrow(new ProviderUnavailableException());

        mockMvc.perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "10"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void invalidBody_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operationId\":\"\",\"amount\":0,\"currency\":\"euro\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(4));
    }

    @Test
    void failureMode_canBeSwitched() throws Exception {
        mockMvc.perform(post("/api/v1/payments/test/failure-mode").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"SLOW\",\"delayMs\":3000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("SLOW"))
                .andExpect(jsonPath("$.delayMs").value(3000));

        mockMvc.perform(get("/api/v1/payments/test/failure-mode"))
                .andExpect(jsonPath("$.mode").value("SLOW"));
    }

    @Test
    void unknownFailureMode_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/payments/test/failure-mode").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"EXPLODE\"}"))
                .andExpect(status().isBadRequest());
    }
}
