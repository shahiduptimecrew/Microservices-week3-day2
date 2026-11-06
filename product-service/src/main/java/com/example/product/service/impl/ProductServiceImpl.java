package com.example.product.service.impl;

import com.example.product.dto.request.ProductPatchRequest;
import com.example.product.dto.request.ProductRequest;
import com.example.product.dto.response.AvailabilityResponse;
import com.example.product.dto.response.PageResponse;
import com.example.product.dto.response.ProductResponse;
import com.example.product.dto.response.ProductV2Response;
import com.example.product.entity.Product;
import com.example.product.exception.DuplicateSkuException;
import com.example.product.exception.ProductNotFoundException;
import com.example.product.mapper.ProductMapper;
import com.example.product.repository.ProductRepository;
import com.example.product.service.ProductService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business logic and transaction boundaries.
 * <p>
 * The class is read-only by default; methods that write override it with {@code @Transactional}.
 * Inside a transaction, changes to a loaded entity are flushed automatically on commit
 * ("dirty checking"), so {@code replace} and {@code patch} need no explicit {@code save}.
 */
@Service
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository repository;
    private final ProductMapper mapper;

    public ProductServiceImpl(ProductRepository repository, ProductMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public ProductResponse create(ProductRequest request) {
        if (repository.existsBySku(request.sku())) {
            throw new DuplicateSkuException(request.sku());
        }
        Product saved = repository.save(mapper.toEntity(request));
        return mapper.toResponse(saved);
    }

    @Override
    public PageResponse<ProductResponse> findAll(Pageable pageable) {
        return PageResponse.from(repository.findAll(pageable), mapper::toResponse);
    }

    @Override
    public ProductResponse findById(Long id) {
        return mapper.toResponse(load(id));
    }

    @Override
    public ProductV2Response findByIdV2(Long id) {
        return mapper.toV2Response(load(id));
    }

    @Override
    @Transactional
    public ProductResponse replace(Long id, ProductRequest request) {
        Product product = load(id);
        ensureSkuIsFree(request.sku(), id);
        mapper.replace(product, request);
        return mapper.toResponse(repository.saveAndFlush(product));
    }

    @Override
    @Transactional
    public ProductResponse patch(Long id, ProductPatchRequest request) {
        Product product = load(id);
        if (request.sku() != null) {
            ensureSkuIsFree(request.sku(), id);
        }
        mapper.patch(product, request);
        return mapper.toResponse(repository.saveAndFlush(product));
    }

    @Override
    @Transactional
    public void delete(Long id) {
        repository.delete(load(id));
    }

    @Override
    public AvailabilityResponse checkAvailability(Long id, int quantity) {
        Product product = load(id);
        return new AvailabilityResponse(
                product.getId(),
                quantity,
                product.getStockQuantity(),
                product.getStockQuantity() >= quantity,
                product.getPrice());
    }

    private Product load(Long id) {
        return repository.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    private void ensureSkuIsFree(String sku, Long ownId) {
        if (repository.existsBySkuAndIdNot(sku, ownId)) {
            throw new DuplicateSkuException(sku);
        }
    }
}
