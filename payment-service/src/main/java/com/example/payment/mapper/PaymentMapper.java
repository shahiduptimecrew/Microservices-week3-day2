package com.example.payment.mapper;

import com.example.payment.dto.response.PaymentResponse;
import com.example.payment.entity.Payment;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {

    public PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOperationId(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus(),
                payment.getFailureReason(),
                payment.getCreatedAt());
    }
}
