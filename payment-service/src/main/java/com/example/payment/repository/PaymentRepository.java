package com.example.payment.repository;

import com.example.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOperationId(String operationId);

    List<Payment> findByOrderIdOrderByIdAsc(Long orderId);
}
