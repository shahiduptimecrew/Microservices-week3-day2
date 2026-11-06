package com.example.gateway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the real gateway on a random port with Eureka switched off, so no service instance exists.
 * That is enough to check the route table and the gateway's own error responses.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "eureka.client.enabled=false")
class GatewayRoutingTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private RouteLocator routeLocator;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void routesPointToEurekaServiceIds() {
        List<Route> routes = routeLocator.getRoutes().collectList().block();

        assertThat(routes).extracting(Route::getId)
                .containsExactlyInAnyOrder("product-service", "order-service", "payment-service");
        assertThat(routes).extracting(route -> route.getUri().toString())
                .containsExactlyInAnyOrder("lb://PRODUCT-SERVICE", "lb://ORDER-SERVICE", "lb://PAYMENT-SERVICE");
    }

    @Test
    void unknownPath_returns404_problemJson() {
        client.get().uri("/api/v1/unknown")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody().jsonPath("$.status").isEqualTo(404);
    }

    @Test
    void noInstanceRegistered_returns503_problemJson_withRequestId() {
        client.get().uri("/api/v1/products/1")
                .header("X-Request-Id", "test-123")
                .exchange()
                .expectStatus().isEqualTo(503)
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON)
                .expectHeader().valueEquals("X-Request-Id", "test-123")
                .expectBody()
                .jsonPath("$.title").isEqualTo("Service Unavailable")
                .jsonPath("$.instance").isEqualTo("/api/v1/products/1");
    }
}
