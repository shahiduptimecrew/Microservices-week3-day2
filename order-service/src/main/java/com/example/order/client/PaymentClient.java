package com.example.order.client;

import com.example.order.config.CircuitBreakerConfiguration;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

/**
 * Calls Payment Service through a circuit breaker.
 * <p>
 * Three protections work together:
 * <ul>
 *   <li><b>Timeout</b> (read timeout 2 s): never wait forever for one call.</li>
 *   <li><b>Circuit breaker</b>: after repeated failures, stop calling for a while and fail fast.</li>
 *   <li><b>Idempotent operationId</b> ({@code order-<id>}): a retry after an unclear outcome can never
 *       charge twice, because Payment Service returns the stored payment.</li>
 * </ul>
 * There is deliberately no automatic retry: the fallback reports {@link PaymentOutcome.Unknown}
 * and the order becomes PENDING. The client can then call {@code retry-payment}.
 */
@Component
public class PaymentClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentClient.class);

    record ChargeRequest(String operationId, Long orderId, BigDecimal amount, String currency) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChargeResponse(Long id, String status, String failureReason) {
    }

    private final RestClient restClient;
    private final CircuitBreakerFactory<?, ?> circuitBreakerFactory;

    public PaymentClient(@Qualifier("paymentRestClient") RestClient restClient,
                         CircuitBreakerFactory<?, ?> circuitBreakerFactory) {
        this.restClient = restClient;
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    public PaymentOutcome pay(String operationId, Long orderId, BigDecimal amount, String currency) {
        return circuitBreakerFactory.create(CircuitBreakerConfiguration.PAYMENT_CIRCUIT_BREAKER).run(
                () -> charge(new ChargeRequest(operationId, orderId, amount, currency)),
                failure -> fallback(operationId, failure));
    }

    private PaymentOutcome charge(ChargeRequest request) {
        ChargeResponse response = restClient.post()
                .uri("/api/v1/payments")
                .body(request)
                .retrieve()
                .body(ChargeResponse.class);
        if (response != null && "SUCCESS".equals(response.status())) {
            return new PaymentOutcome.Paid(response.id());
        }
        String reason = response == null ? "Empty response from Payment Service" : response.failureReason();
        return new PaymentOutcome.Declined(response == null ? null : response.id(), reason);
    }

    /** Called for every exception, and immediately (without a network call) while the circuit is OPEN. */
    private PaymentOutcome fallback(String operationId, Throwable failure) {
        PaymentOutcome outcome = classify(failure);
        log.warn("Payment for {} did not complete normally: {} ({})", operationId, outcome, failure.toString());
        return outcome;
    }

    private static PaymentOutcome classify(Throwable failure) {
        if (DownstreamErrors.findCause(failure, CallNotPermittedException.class) != null) {
            return new PaymentOutcome.Unknown("Payment circuit breaker is OPEN: payment was not attempted");
        }
        HttpClientErrorException clientError = DownstreamErrors.findCause(failure, HttpClientErrorException.class);
        if (clientError != null) {
            return new PaymentOutcome.Rejected("Payment Service rejected the request: " + clientError.getStatusCode());
        }
        if (DownstreamErrors.isTimeout(failure)) {
            return new PaymentOutcome.Unknown("Payment Service did not answer in time");
        }
        HttpServerErrorException serverError = DownstreamErrors.findCause(failure, HttpServerErrorException.class);
        if (serverError != null) {
            return new PaymentOutcome.Unknown("Payment Service returned " + serverError.getStatusCode());
        }
        if (DownstreamErrors.findCause(failure, IllegalStateException.class) != null) {
            return new PaymentOutcome.Unknown("No Payment Service instance is available");
        }
        return new PaymentOutcome.Unknown("Payment Service is unreachable");
    }
}
