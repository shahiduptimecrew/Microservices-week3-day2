package com.example.product.mapper;

import com.example.product.dto.request.ProductPatchRequest;
import com.example.product.dto.request.ProductRequest;
import com.example.product.dto.response.ProductResponse;
import com.example.product.dto.response.ProductV2Response;
import com.example.product.entity.Product;
import org.springframework.stereotype.Component;

/**
 * Converts between entities and DTOs. Written by hand so the mapping is visible;
 * MapStruct would generate the same code.
 */
@Component
public class ProductMapper {

    /** Demo shop sells in one currency. */
    public static final String CURRENCY = "EUR";

    public Product toEntity(ProductRequest request) {
        return new Product(
                request.sku(),
                request.name(),
                request.description(),
                request.price(),
                request.stockQuantity());
    }

    /** PUT: overwrite every field. */
    public void replace(Product product, ProductRequest request) {
        product.setSku(request.sku());
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setStockQuantity(request.stockQuantity());
    }

    /** PATCH: overwrite only the fields that were sent. */
    public void patch(Product product, ProductPatchRequest request) {
        if (request.sku() != null) product.setSku(request.sku());
        if (request.name() != null) product.setName(request.name());
        if (request.description() != null) product.setDescription(request.description());
        if (request.price() != null) product.setPrice(request.price());
        if (request.stockQuantity() != null) product.setStockQuantity(request.stockQuantity());
    }

    public ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }

    public ProductV2Response toV2Response(Product product) {
        return new ProductV2Response(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                new ProductV2Response.Money(product.getPrice(), CURRENCY),
                new ProductV2Response.Stock(product.getStockQuantity(), product.getStockQuantity() > 0));
    }
}
