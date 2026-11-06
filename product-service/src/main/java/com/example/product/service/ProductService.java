package com.example.product.service;

import com.example.product.dto.request.ProductPatchRequest;
import com.example.product.dto.request.ProductRequest;
import com.example.product.dto.response.AvailabilityResponse;
import com.example.product.dto.response.PageResponse;
import com.example.product.dto.response.ProductResponse;
import com.example.product.dto.response.ProductV2Response;
import org.springframework.data.domain.Pageable;

/** Business operations of the Product Service. The controller depends on this interface only. */
public interface ProductService {

    ProductResponse create(ProductRequest request);

    PageResponse<ProductResponse> findAll(Pageable pageable);

    ProductResponse findById(Long id);

    ProductV2Response findByIdV2(Long id);

    ProductResponse replace(Long id, ProductRequest request);

    ProductResponse patch(Long id, ProductPatchRequest request);

    void delete(Long id);

    AvailabilityResponse checkAvailability(Long id, int quantity);
}
