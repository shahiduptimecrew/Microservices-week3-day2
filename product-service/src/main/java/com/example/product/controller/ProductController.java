package com.example.product.controller;

import com.example.product.dto.request.ProductPatchRequest;
import com.example.product.dto.request.ProductRequest;
import com.example.product.dto.response.AvailabilityResponse;
import com.example.product.dto.response.PageResponse;
import com.example.product.dto.response.ProductResponse;
import com.example.product.service.ProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

/**
 * HTTP layer only: maps URLs and verbs to service calls and chooses status codes and headers.
 * No business rules live here.
 *
 * <pre>
 * POST   /api/v1/products                      201 Created + Location
 * GET    /api/v1/products?page=&size=&sort=    200 OK (paged)
 * GET    /api/v1/products/{id}                 200 OK | 404
 * PUT    /api/v1/products/{id}                 200 OK (full replace, idempotent)
 * PATCH  /api/v1/products/{id}                 200 OK (partial update)
 * DELETE /api/v1/products/{id}                 204 No Content | 404
 * GET    /api/v1/products/{id}/availability    200 OK
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse created = productService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /**
     * Example: {@code ?page=0&size=5&sort=price,desc}. Page size is capped at 100 (application.yml).
     * {@code @ParameterObject} makes Swagger show page, size and sort as separate query parameters
     * instead of one required "pageable" object.
     */
    @GetMapping
    public PageResponse<ProductResponse> findAll(
            @ParameterObject
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return productService.findAll(pageable);
    }

    @GetMapping("/{id}")
    public ProductResponse findById(@PathVariable Long id) {
        return productService.findById(id);
    }

    @PutMapping("/{id}")
    public ProductResponse replace(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return productService.replace(id, request);
    }

    @PatchMapping("/{id}")
    public ProductResponse patch(@PathVariable Long id, @Valid @RequestBody ProductPatchRequest request) {
        return productService.patch(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Called by Order Service. {@code quantity} is validated by Spring MVC's built-in
     * method validation: {@code quantity=0} gives 400 problem+json.
     */
    @GetMapping("/{id}/availability")
    public AvailabilityResponse availability(@PathVariable Long id,
                                             @RequestParam @Min(1) @Max(10_000) int quantity) {
        return productService.checkAvailability(id, quantity);
    }
}
