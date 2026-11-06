package com.example.product.controller;

import com.example.product.dto.request.ProductRequest;
import com.example.product.dto.response.AvailabilityResponse;
import com.example.product.dto.response.PageResponse;
import com.example.product.dto.response.ProductResponse;
import com.example.product.dto.response.ProductV2Response;
import com.example.product.exception.DuplicateSkuException;
import com.example.product.exception.ProductNotFoundException;
import com.example.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer slice test: only the controllers and the exception handler are loaded.
 * The service is mocked, so these tests check HTTP concerns: status codes, headers,
 * validation and the problem+json error format.
 */
@WebMvcTest(controllers = {ProductController.class, ProductV2Controller.class},
        properties = "eureka.client.enabled=false")
class ProductControllerTest {

    private static final String VALID_BODY = """
            {"sku":"TAB-1","name":"Tablet","description":"10 inch","price":199.00,"stockQuantity":3}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    private static ProductResponse sample(long id) {
        Instant now = Instant.parse("2026-11-05T10:00:00Z");
        return new ProductResponse(id, "TAB-1", "Tablet", "10 inch", new BigDecimal("199.00"), 3, now, now);
    }

    @Test
    void post_returns201_withLocationHeader() throws Exception {
        when(productService.create(any(ProductRequest.class))).thenReturn(sample(6));

        mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/v1/products/6"))
                .andExpect(jsonPath("$.id").value(6))
                .andExpect(jsonPath("$.sku").value("TAB-1"));
    }

    @Test
    void post_returns400_problemJson_listingEveryInvalidField() throws Exception {
        String invalid = """
                {"sku":"bad sku","name":"","price":0,"stockQuantity":-1}
                """;

        mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(invalid))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.length()").value(4));
        verifyNoInteractions(productService);
    }

    @Test
    void post_returns400_whenJsonIsMalformed() throws Exception {
        mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void post_returns409_whenSkuIsDuplicate() throws Exception {
        when(productService.create(any())).thenThrow(new DuplicateSkuException("TAB-1"));

        mockMvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("https://example.com/problems/duplicate-sku"))
                .andExpect(jsonPath("$.sku").value("TAB-1"));
    }

    @Test
    void get_returns404_problemJson_whenMissing() throws Exception {
        when(productService.findById(99L)).thenThrow(new ProductNotFoundException(99L));

        mockMvc.perform(get("/api/v1/products/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Product not found"))
                .andExpect(jsonPath("$.instance").value("/api/v1/products/99"));
    }

    @Test
    void get_returns400_whenIdIsNotANumber() throws Exception {
        mockMvc.perform(get("/api/v1/products/abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void list_returnsPageShape() throws Exception {
        when(productService.findAll(any(Pageable.class)))
                .thenReturn(new PageResponse<>(List.of(sample(1)), 0, 20, 1, 1, true, true));

        mockMvc.perform(get("/api/v1/products").param("page", "0").param("size", "20").param("sort", "price,desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/products/1"))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_returns404_whenMissing() throws Exception {
        doThrow(new ProductNotFoundException(5L)).when(productService).delete(5L);

        mockMvc.perform(delete("/api/v1/products/5"))
                .andExpect(status().isNotFound());
    }

    @Test
    void availability_returns200() throws Exception {
        when(productService.checkAvailability(1L, 2))
                .thenReturn(new AvailabilityResponse(1L, 2, 3, true, new BigDecimal("199.00")));

        mockMvc.perform(get("/api/v1/products/1/availability").param("quantity", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void availability_returns400_whenQuantityIsZero() throws Exception {
        mockMvc.perform(get("/api/v1/products/1/availability").param("quantity", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
        verifyNoInteractions(productService);
    }

    @Test
    void v2_returnsNewShape() throws Exception {
        when(productService.findByIdV2(eq(1L))).thenReturn(new ProductV2Response(1L, "TAB-1", "Tablet", null,
                new ProductV2Response.Money(new BigDecimal("199.00"), "EUR"),
                new ProductV2Response.Stock(3, true)));

        mockMvc.perform(get("/api/v2/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.price.currency").value("EUR"))
                .andExpect(jsonPath("$.stock.inStock").value(true));
    }
}
