package com.example.order.entity;

/**
 * Lifecycle of an order.
 * <pre>
 * CREATED ──payment SUCCESS──▶ CONFIRMED
 *    │ ────payment FAILED───▶ FAILED      (declined: the customer was not charged)
 *    └─────outcome unknown──▶ PENDING     (timeout / provider down / circuit open)
 *                                │
 *                                └─retry-payment─▶ CONFIRMED | FAILED | PENDING
 * </pre>
 * A PENDING order is never shown as paid. It is resolved by retrying with the same
 * operationId, which Payment Service deduplicates, so the customer is never charged twice.
 */
public enum OrderStatus {
    CREATED,
    CONFIRMED,
    FAILED,
    PENDING
}
