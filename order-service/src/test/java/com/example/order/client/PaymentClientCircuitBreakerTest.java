package com.example.order.client;

import com.example.order.config.CircuitBreakerConfiguration;
import com.example.order.config.ClientProperties.CircuitBreakerSettings;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Walks the payment circuit breaker through CLOSED → OPEN → HALF_OPEN → CLOSED with a fake
 * Payment Service (MockRestServiceServer). Same settings class as production, shorter wait.
 */
class PaymentClientCircuitBreakerTest {

    private static final String URL = "http://payment/api/v1/payments";
    private static final String SUCCESS_BODY = "{\"id\":1,\"status\":\"SUCCESS\"}";

    private MockRestServiceServer server;
    private Resilience4JCircuitBreakerFactory factory;
    private PaymentClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://payment");
        server = MockRestServiceServer.bindTo(builder).build();

        factory = new Resilience4JCircuitBreakerFactory(
                CircuitBreakerRegistry.ofDefaults(), TimeLimiterRegistry.ofDefaults(), null);
        CircuitBreakerConfiguration.configure(factory,
                new CircuitBreakerSettings(50, 4, 4, Duration.ofMillis(300), 2, Duration.ofSeconds(2)));

        client = new PaymentClient(builder.build(), factory);
    }

    private PaymentOutcome pay() {
        return client.pay("order-1", 1L, new BigDecimal("10.00"), "EUR");
    }

    private CircuitBreaker.State state() {
        return factory.getCircuitBreakerRegistry().circuitBreaker(CircuitBreakerConfiguration.PAYMENT_CIRCUIT_BREAKER).getState();
    }

    @Test
    void success_isPaid_andCircuitStaysClosed() {
        server.expect(requestTo(URL)).andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));

        assertThat(pay()).isEqualTo(new PaymentOutcome.Paid(1L));
        assertThat(state()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void declined_isABusinessResult_notAFailure() {
        server.expect(times(4), requestTo(URL)).andRespond(
                withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"id\":2,\"status\":\"FAILED\",\"failureReason\":\"Card declined\"}"));

        for (int i = 0; i < 4; i++) {
            assertThat(pay()).isEqualTo(new PaymentOutcome.Declined(2L, "Card declined"));
        }
        assertThat(state()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void clientErrors_doNotOpenTheCircuit() {
        server.expect(times(4), requestTo(URL)).andRespond(withStatus(HttpStatus.CONFLICT));

        for (int i = 0; i < 4; i++) {
            assertThat(pay()).isInstanceOf(PaymentOutcome.Rejected.class);
        }
        assertThat(state()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void repeatedServerErrors_openTheCircuit_thenItRecovers() {
        // CLOSED: 4 failures fill the window → failure rate 100% ≥ 50% → OPEN
        server.expect(times(4), requestTo(URL)).andRespond(withServerError());
        for (int i = 0; i < 4; i++) {
            assertThat(pay()).isInstanceOf(PaymentOutcome.Unknown.class);
        }
        assertThat(state()).isEqualTo(CircuitBreaker.State.OPEN);

        // OPEN: fails fast, no HTTP call is made (the mock server would reject an unexpected request)
        PaymentOutcome rejectedFast = pay();
        assertThat(rejectedFast).isEqualTo(new PaymentOutcome.Unknown("Payment circuit breaker is OPEN: payment was not attempted"));
        server.verify();

        // After the wait duration → HALF_OPEN automatically
        await().atMost(Duration.ofSeconds(2)).until(() -> state() == CircuitBreaker.State.HALF_OPEN);

        // HALF_OPEN: 2 successful trial calls → CLOSED
        server.reset();
        server.expect(times(2), requestTo(URL)).andRespond(withSuccess(SUCCESS_BODY, MediaType.APPLICATION_JSON));
        assertThat(pay()).isInstanceOf(PaymentOutcome.Paid.class);
        assertThat(pay()).isInstanceOf(PaymentOutcome.Paid.class);
        assertThat(state()).isEqualTo(CircuitBreaker.State.CLOSED);
        server.verify();
    }
}
