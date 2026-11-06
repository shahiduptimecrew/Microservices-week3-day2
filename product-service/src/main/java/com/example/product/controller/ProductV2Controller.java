package com.example.product.controller;

import com.example.product.dto.response.ProductV2Response;
import com.example.product.service.ProductService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API versioning demo (URI versioning).
 * <p>
 * v1 stays unchanged for existing clients. v2 returns a different shape for the same resource.
 * Both versions read the same data through the same service.
 */
@RestController
@RequestMapping("/api/v2/products")
public class ProductV2Controller {

    private final ProductService productService;

    public ProductV2Controller(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping("/{id}")
    public ProductV2Response findById(@PathVariable Long id) {
        return productService.findByIdV2(id);
    }
}
