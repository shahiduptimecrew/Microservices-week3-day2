package com.example.order.client;

/**
 * Result of asking Payment Service to charge an order. A sealed type forces the caller
 * to handle every case explicitly.
 */
public sealed interface PaymentOutcome {

    /** Charged successfully. */
    record Paid(Long paymentId) implements PaymentOutcome {
    }

    /** Payment Service answered: the provider declined. Nothing was charged. */
    record Declined(Long paymentId, String reason) implements PaymentOutcome {
    }

    /** Payment Service rejected our request (4xx). Not retryable without a fix. */
    record Rejected(String reason) implements PaymentOutcome {
    }

    /**
     * No definite answer: timeout, 5xx, no instance, or circuit OPEN.
     * The charge may or may not have happened, so the order must not be treated as paid or failed.
     */
    record Unknown(String reason) implements PaymentOutcome {
    }
}
