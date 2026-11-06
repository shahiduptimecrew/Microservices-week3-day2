package com.example.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Settings for the downstream calls, bound from {@code order.clients.*} in application.yml.
 *
 * @param product               Product Service endpoint and timeouts
 * @param payment               Payment Service endpoint and timeouts
 * @param paymentCircuitBreaker circuit breaker around the payment call
 */
@ConfigurationProperties("order.clients")
public record ClientProperties(Endpoint product, Endpoint payment, CircuitBreakerSettings paymentCircuitBreaker) {

    /**
     * @param baseUrl        {@code http://<SERVICE-ID>}: resolved by Spring Cloud LoadBalancer via Eureka
     * @param connectTimeout max time to open the TCP connection
     * @param readTimeout    max time to wait for the response
     */
    public record Endpoint(String baseUrl, Duration connectTimeout, Duration readTimeout) {
    }

    /**
     * @param failureRateThreshold          % of failed calls in the window that opens the circuit
     * @param slidingWindowSize             number of recent calls considered
     * @param minimumNumberOfCalls          calls needed before the failure rate is evaluated
     * @param waitDurationInOpenState       how long the circuit stays OPEN before trying HALF_OPEN
     * @param permittedCallsInHalfOpenState trial calls in HALF_OPEN; all succeed means CLOSED again
     * @param timeLimit                     hard upper bound for one protected call
     */
    public record CircuitBreakerSettings(float failureRateThreshold,
                                         int slidingWindowSize,
                                         int minimumNumberOfCalls,
                                         Duration waitDurationInOpenState,
                                         int permittedCallsInHalfOpenState,
                                         Duration timeLimit) {
    }
}
