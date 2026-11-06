package com.example.order.client;

import com.example.order.exception.OrderExceptions.DownstreamServiceException;
import com.example.order.exception.OrderExceptions.ProductNotFoundException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;

/**
 * Calls Product Service synchronously (REST).
 * <p>
 * Synchronous is the right choice here: Order Service cannot continue without the answer
 * (is it in stock, and at what price?). Failures are translated into clear HTTP errors:
 * unknown product gives 404, slow Product Service gives 504, unreachable gives 503.
 */
@Component
public class ProductClient {

    public static final String SERVICE = "product-service";

    /** Subset of Product Service's availability response that Order Service needs. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ProductAvailability(Long productId, int availableQuantity, boolean available, BigDecimal unitPrice) {
    }

    private final RestClient restClient;

    public ProductClient(@Qualifier("productRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public ProductAvailability checkAvailability(Long productId, int quantity) {
        try {
            return restClient.get()
                    .uri("/api/v1/products/{id}/availability?quantity={quantity}", productId, quantity)
                    .retrieve()
                    .body(ProductAvailability.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ProductNotFoundException(productId);
        } catch (RestClientException | IllegalStateException e) {
            // IllegalStateException: LoadBalancer found no registered instance.
            if (DownstreamErrors.isTimeout(e)) {
                throw new DownstreamServiceException(SERVICE, HttpStatus.GATEWAY_TIMEOUT,
                        "Product Service did not answer in time", e);
            }
            throw new DownstreamServiceException(SERVICE, HttpStatus.SERVICE_UNAVAILABLE,
                    "Product Service is unavailable", e);
        }
    }
}
