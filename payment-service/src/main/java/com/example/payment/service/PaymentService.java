package com.example.payment.service;

import com.example.payment.dto.request.PaymentRequest;
import com.example.payment.dto.response.PaymentResponse;

import java.util.List;

public interface PaymentService {

    /** Outcome of {@link #process}: the payment and whether it was created now or found as a duplicate. */
    record ProcessResult(PaymentResponse payment, boolean created) {
    }

    ProcessResult process(PaymentRequest request);

    PaymentResponse findById(Long id);

    List<PaymentResponse> find(String operationId, Long orderId);
}
