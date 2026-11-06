package com.example.payment.repository;

import com.example.payment.entity.Payment;
import com.example.payment.entity.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "eureka.client.enabled=false")
class PaymentRepositoryTest {

    @Autowired
    private PaymentRepository repository;

    private Payment payment(String operationId) {
        return new Payment(operationId, 1L, new BigDecimal("10.00"), "EUR", PaymentStatus.SUCCESS, null);
    }

    @Test
    void operationId_isUnique_atDatabaseLevel() {
        repository.saveAndFlush(payment("order-1"));

        assertThatThrownBy(() -> repository.saveAndFlush(payment("order-1")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByOperationId() {
        repository.saveAndFlush(payment("order-7"));

        assertThat(repository.findByOperationId("order-7")).isPresent();
        assertThat(repository.findByOperationId("order-8")).isEmpty();
    }
}
