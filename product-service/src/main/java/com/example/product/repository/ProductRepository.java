package com.example.product.repository;

import com.example.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data generates the implementation at runtime, including paging and sorting
 * ({@code findAll(Pageable)}) inherited from {@link JpaRepository}.
 */
public interface ProductRepository extends JpaRepository<Product, Long> {

    boolean existsBySku(String sku);

    boolean existsBySkuAndIdNot(String sku, Long id);
}
