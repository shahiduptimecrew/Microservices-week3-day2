package com.example.order.entity;

/** What Order Service knows about the payment of an order. */
public enum PaymentStatus {
    /** Payment Service has not been called yet. */
    NOT_INITIATED,
    /** Payment Service confirmed the charge. */
    SUCCESS,
    /** Payment Service answered: declined. */
    FAILED,
    /** No answer (timeout, 5xx, circuit open). The charge may or may not have happened. */
    PENDING
}
