package com.example.product.exception;

/**
 * Mapped to 409 Conflict by {@link GlobalExceptionHandler}.
 * The request is well-formed (so not 400), but it conflicts with the current state of the server.
 */
public class DuplicateSkuException extends RuntimeException {

    private final String sku;

    public DuplicateSkuException(String sku) {
        super("A product with SKU '" + sku + "' already exists");
        this.sku = sku;
    }

    public String getSku() {
        return sku;
    }
}
