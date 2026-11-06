package com.example.order.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.HttpClientErrorException;

/**
 * Circuit breaker around the call to Payment Service (Resilience4j via Spring Cloud CircuitBreaker).
 * <pre>
 *   CLOSED ──failure rate ≥ threshold──▶ OPEN ──wait duration──▶ HALF_OPEN
 *     ▲                                   ▲                         │
 *     └──────trial calls succeed──────────┼─────────────────────────┘
 *                                         └──trial call fails───────┘
 * </pre>
 * While OPEN, calls fail immediately (no network call) and the fallback marks the order PENDING.
 * This protects Order Service threads and gives Payment Service time to recover.
 */
@Configuration
public class CircuitBreakerConfiguration {

    public static final String PAYMENT_CIRCUIT_BREAKER = "payment";

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerConfiguration.class);

    @Bean
    Customizer<Resilience4JCircuitBreakerFactory> paymentCircuitBreakerCustomizer(ClientProperties properties) {
        return factory -> configure(factory, properties.paymentCircuitBreaker());
    }

    /** Public and static so the circuit-breaker test can apply exactly the same settings. */
    public static void configure(Resilience4JCircuitBreakerFactory factory, ClientProperties.CircuitBreakerSettings s) {
        CircuitBreakerConfig circuitBreakerConfig = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(s.slidingWindowSize())
                .minimumNumberOfCalls(s.minimumNumberOfCalls())
                .failureRateThreshold(s.failureRateThreshold())
                .waitDurationInOpenState(s.waitDurationInOpenState())
                .permittedNumberOfCallsInHalfOpenState(s.permittedCallsInHalfOpenState())
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                // 4xx means "our request was wrong", not "Payment Service is unhealthy":
                // it must not push the circuit towards OPEN.
                .ignoreExceptions(HttpClientErrorException.class)
                .build();

        factory.configure(builder -> builder
                        .circuitBreakerConfig(circuitBreakerConfig)
                        .timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(s.timeLimit()).build()),
                PAYMENT_CIRCUIT_BREAKER);

        factory.addCircuitBreakerCustomizer(circuitBreaker -> circuitBreaker.getEventPublisher()
                        .onStateTransition(event -> log.warn("Circuit breaker '{}': {}",
                                event.getCircuitBreakerName(), event.getStateTransition())),
                PAYMENT_CIRCUIT_BREAKER);
    }
}
