package com.example.payment.exception;

public class PaymentNotFoundException extends RuntimeException {

    private final Long paymentId;

    public PaymentNotFoundException(Long paymentId) {
        super("Payment with id " + paymentId + " was not found");
        this.paymentId = paymentId;
    }

    public Long getPaymentId() {
        return paymentId;
    }
}
