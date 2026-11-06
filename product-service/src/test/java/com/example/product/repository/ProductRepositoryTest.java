package com.example.product.repository;

import com.example.product.entity.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JPA slice test against a fresh embedded H2 database: checks the mapping, the unique
 * constraint, the derived queries and paging. Each test runs in a transaction that is rolled back.
 */
@DataJpaTest(properties = "eureka.client.enabled=false")
class ProductRepositoryTest {

    @Autowired
    private ProductRepository repository;

    private Product product(String sku, String price) {
        return new Product(sku, "Name " + sku, null, new BigDecimal(price), 1);
    }

    @Test
    void save_setsIdAndTimestamps() {
        Product saved = repository.saveAndFlush(product("A-1", "10.00"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void existsBySku_andExistsBySkuAndIdNot() {
        Product saved = repository.saveAndFlush(product("A-1", "10.00"));

        assertThat(repository.existsBySku("A-1")).isTrue();
        assertThat(repository.existsBySku("B-1")).isFalse();
        assertThat(repository.existsBySkuAndIdNot("A-1", saved.getId())).isFalse();
        assertThat(repository.existsBySkuAndIdNot("A-1", saved.getId() + 1)).isTrue();
    }

    @Test
    void uniqueConstraint_rejectsDuplicateSku() {
        repository.saveAndFlush(product("A-1", "10.00"));

        assertThatThrownBy(() -> repository.saveAndFlush(product("A-1", "20.00")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findAll_pagesAndSorts() {
        repository.saveAndFlush(product("A-1", "30.00"));
        repository.saveAndFlush(product("A-2", "10.00"));
        repository.saveAndFlush(product("A-3", "20.00"));

        Page<Product> page = repository.findAll(PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "price")));

        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getTotalPages()).isEqualTo(2);
        assertThat(page.getContent()).extracting(Product::getSku).containsExactly("A-1", "A-3");
    }
}
