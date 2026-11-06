package com.example.order.exception;

import org.springframework.http.HttpStatus;

/**
 * All application exceptions of Order Service, grouped in one file to keep the demo compact.
 * Each one is mapped to a problem+json response by {@link GlobalExceptionHandler}.
 */
public final class OrderExceptions {

    private OrderExceptions() {
    }

    /** 404: the order id does not exist. */
    public static class OrderNotFoundException extends RuntimeException {
        private final Long orderId;

        public OrderNotFoundException(Long orderId) {
            super("Order with id " + orderId + " was not found");
            this.orderId = orderId;
        }

        public Long getOrderId() { return orderId; }
    }

    /** 404: the order refers to a product that Product Service does not know. */
    public static class ProductNotFoundException extends RuntimeException {
        private final Long productId;

        public ProductNotFoundException(Long productId) {
            super("Product with id " + productId + " does not exist");
            this.productId = productId;
        }

        public Long getProductId() { return productId; }
    }

    /** 409: not enough stock. Valid request, but it conflicts with the current stock level. */
    public static class InsufficientStockException extends RuntimeException {
        private final Long productId;
        private final int requested;
        private final int available;

        public InsufficientStockException(Long productId, int requested, int available) {
            super("Only " + available + " unit(s) of product " + productId + " available, " + requested + " requested");
            this.productId = productId;
            this.requested = requested;
            this.available = available;
        }

        public Long getProductId() { return productId; }
        public int getRequested() { return requested; }
        public int getAvailable() { return available; }
    }

    /** 409: the Idempotency-Key was already used with a different request body. */
    public static class IdempotencyKeyMismatchException extends RuntimeException {
        public IdempotencyKeyMismatchException(String key) {
            super("Idempotency-Key '" + key + "' was already used with a different request body");
        }
    }

    /** 409: a request with the same Idempotency-Key is still being processed. */
    public static class IdempotencyRequestInProgressException extends RuntimeException {
        public IdempotencyRequestInProgressException(String key) {
            super("A request with Idempotency-Key '" + key + "' is still being processed. Retry shortly.");
        }
    }

    /** 409: retry-payment was called on an order that is not PENDING. */
    public static class OrderNotAwaitingPaymentException extends RuntimeException {
        public OrderNotAwaitingPaymentException(Long orderId, Object status) {
            super("Order " + orderId + " is " + status + "; only PENDING orders can retry payment");
        }
    }

    /** 503 (unreachable / no instance / 5xx) or 504 (timeout) from a downstream service. */
    public static class DownstreamServiceException extends RuntimeException {
        private final HttpStatus status;
        private final String service;

        public DownstreamServiceException(String service, HttpStatus status, String message, Throwable cause) {
            super(message, cause);
            this.service = service;
            this.status = status;
        }

        public HttpStatus getStatus() { return status; }
        public String getService() { return service; }
    }
}
