package com.example.payment.entity;

/**
 * Final outcome of a payment.
 * <ul>
 *   <li>{@code SUCCESS}: money was taken.</li>
 *   <li>{@code FAILED}: the (simulated) provider declined it, for example "card declined".
 *       This is a business result, returned with a normal 2xx status, not an HTTP error.</li>
 * </ul>
 */
public enum PaymentStatus {
    SUCCESS,
    FAILED
}
