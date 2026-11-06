package com.example.payment.service.impl;

import com.example.payment.dto.request.PaymentRequest;
import com.example.payment.entity.Payment;
import com.example.payment.entity.PaymentStatus;
import com.example.payment.exception.OperationIdConflictException;
import com.example.payment.exception.ProviderUnavailableException;
import com.example.payment.mapper.PaymentMapper;
import com.example.payment.repository.PaymentRepository;
import com.example.payment.service.FailureMode;
import com.example.payment.service.PaymentProviderSimulator;
import com.example.payment.service.PaymentService.ProcessResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentRepository repository;

    private PaymentProviderSimulator provider;
    private PaymentServiceImpl service;

    private final PaymentRequest request = new PaymentRequest("order-1", 1L, new BigDecimal("50.00"), null);

    @BeforeEach
    void setUp() {
        provider = new PaymentProviderSimulator();
        service = new PaymentServiceImpl(repository, provider, new PaymentMapper());
    }

    @Test
    void newOperation_isChargedAndStored() {
        when(repository.findByOperationId("order-1")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        ProcessResult result = service.process(request);

        assertThat(result.created()).isTrue();
        assertThat(result.payment().status()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(result.payment().currency()).isEqualTo("EUR");
    }

    @Test
    void duplicateOperation_returnsStoredPayment_withoutChargingAgain() {
        Payment stored = new Payment("order-1", 1L, new BigDecimal("50.00"), "EUR", PaymentStatus.SUCCESS, null);
        when(repository.findByOperationId("order-1")).thenReturn(Optional.of(stored));
        // If the provider were called, ERROR mode would throw.
        provider.setMode(FailureMode.ERROR, null);

        ProcessResult result = service.process(request);

        assertThat(result.created()).isFalse();
        assertThat(result.payment().status()).isEqualTo(PaymentStatus.SUCCESS);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void sameOperationId_withDifferentAmount_isConflict() {
        Payment stored = new Payment("order-1", 1L, new BigDecimal("99.00"), "EUR", PaymentStatus.SUCCESS, null);
        when(repository.findByOperationId("order-1")).thenReturn(Optional.of(stored));

        assertThatThrownBy(() -> service.process(request)).isInstanceOf(OperationIdConflictException.class);
    }

    @Test
    void declineMode_storesFailedPayment() {
        provider.setMode(FailureMode.DECLINE, null);
        when(repository.findByOperationId("order-1")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        ProcessResult result = service.process(request);

        assertThat(result.payment().status()).isEqualTo(PaymentStatus.FAILED);
        assertThat(result.payment().failureReason()).contains("declined");
    }

    @Test
    void errorMode_throws_andStoresNothing() {
        provider.setMode(FailureMode.ERROR, null);
        when(repository.findByOperationId("order-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.process(request)).isInstanceOf(ProviderUnavailableException.class);
        verify(repository, never()).saveAndFlush(any());
    }
}
