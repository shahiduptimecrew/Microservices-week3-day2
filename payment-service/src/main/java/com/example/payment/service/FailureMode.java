package com.example.payment.service;

/**
 * How the simulated payment provider behaves. Switched at runtime for the demo.
 * <ul>
 *   <li>{@code NONE}: every payment succeeds.</li>
 *   <li>{@code ERROR}: the provider is down. The API answers 503 and stores nothing.
 *       A technical failure: it counts towards opening Order Service's circuit breaker.</li>
 *   <li>{@code SLOW}: every payment takes {@code delayMs} before succeeding.
 *       Order Service's 2-second read timeout gives up first: also a technical failure.</li>
 *   <li>{@code DECLINE}: the provider declines the card. Stored as {@code FAILED} and returned
 *       with 201. A business failure: it must <b>not</b> open the circuit breaker.</li>
 * </ul>
 */
public enum FailureMode {
    NONE,
    ERROR,
    SLOW,
    DECLINE
}
