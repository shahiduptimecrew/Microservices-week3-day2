package com.example.product.service.impl;

import com.example.product.dto.request.ProductPatchRequest;
import com.example.product.dto.request.ProductRequest;
import com.example.product.dto.response.AvailabilityResponse;
import com.example.product.dto.response.ProductResponse;
import com.example.product.entity.Product;
import com.example.product.exception.DuplicateSkuException;
import com.example.product.exception.ProductNotFoundException;
import com.example.product.mapper.ProductMapper;
import com.example.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pure unit test: no Spring context, the repository is a Mockito mock. */
@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository repository;

    private ProductServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ProductServiceImpl(repository, new ProductMapper());
    }

    @Test
    void create_savesProduct_whenSkuIsNew() {
        ProductRequest request = new ProductRequest("TAB-1", "Tablet", null, new BigDecimal("199.00"), 3);
        when(repository.existsBySku("TAB-1")).thenReturn(false);
        when(repository.save(any(Product.class))).thenAnswer(inv -> inv.getArgument(0));

        ProductResponse response = service.create(request);

        assertThat(response.sku()).isEqualTo("TAB-1");
        assertThat(response.price()).isEqualByComparingTo("199.00");
        verify(repository).save(any(Product.class));
    }

    @Test
    void create_throwsConflict_whenSkuExists() {
        ProductRequest request = new ProductRequest("TAB-1", "Tablet", null, new BigDecimal("199.00"), 3);
        when(repository.existsBySku("TAB-1")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(DuplicateSkuException.class)
                .hasMessageContaining("TAB-1");
        verify(repository, never()).save(any());
    }

    @Test
    void findById_throwsNotFound_whenMissing() {
        when(repository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(42L))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("42");
    }

    @Test
    void patch_changesOnlyFieldsThatWereSent() {
        Product product = new Product("TAB-1", "Tablet", "old description", new BigDecimal("199.00"), 3);
        when(repository.findById(1L)).thenReturn(Optional.of(product));
        when(repository.saveAndFlush(product)).thenReturn(product);

        ProductResponse response = service.patch(1L, new ProductPatchRequest(null, null, null, new BigDecimal("149.00"), null));

        assertThat(response.price()).isEqualByComparingTo("149.00");
        assertThat(response.name()).isEqualTo("Tablet");
        assertThat(response.description()).isEqualTo("old description");
        assertThat(response.stockQuantity()).isEqualTo(3);
    }

    @Test
    void replace_throwsConflict_whenSkuBelongsToAnotherProduct() {
        Product product = new Product("TAB-1", "Tablet", null, new BigDecimal("199.00"), 3);
        when(repository.findById(1L)).thenReturn(Optional.of(product));
        when(repository.existsBySkuAndIdNot("TAB-2", 1L)).thenReturn(true);

        ProductRequest request = new ProductRequest("TAB-2", "Tablet", null, new BigDecimal("199.00"), 3);

        assertThatThrownBy(() -> service.replace(1L, request)).isInstanceOf(DuplicateSkuException.class);
    }

    @Test
    void availability_isTrue_whenStockIsSufficient() {
        Product product = new Product("TAB-1", "Tablet", null, new BigDecimal("199.00"), 3);
        when(repository.findById(1L)).thenReturn(Optional.of(product));

        AvailabilityResponse response = service.checkAvailability(1L, 3);

        assertThat(response.available()).isTrue();
        assertThat(response.availableQuantity()).isEqualTo(3);
        assertThat(response.unitPrice()).isEqualByComparingTo("199.00");
    }

    @Test
    void availability_isFalse_whenStockIsTooLow() {
        Product product = new Product("TAB-1", "Tablet", null, new BigDecimal("199.00"), 3);
        when(repository.findById(1L)).thenReturn(Optional.of(product));

        assertThat(service.checkAvailability(1L, 4).available()).isFalse();
    }

    @Test
    void delete_throwsNotFound_whenMissing() {
        when(repository.findById(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(7L)).isInstanceOf(ProductNotFoundException.class);
        verify(repository, never()).delete(any());
    }
}
