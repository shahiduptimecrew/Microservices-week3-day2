package com.example.product.exception;

/** Mapped to 404 Not Found by {@link GlobalExceptionHandler}. */
public class ProductNotFoundException extends RuntimeException {

    private final Long productId;

    public ProductNotFoundException(Long productId) {
        super("Product with id " + productId + " was not found");
        this.productId = productId;
    }

    public Long getProductId() {
        return productId;
    }
}
