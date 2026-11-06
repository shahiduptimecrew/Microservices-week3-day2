package com.example.product.config;

import com.example.product.entity.Product;
import com.example.product.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.List;

/**
 * Demo data, so the API has something to show right after start-up.
 * <p>
 * Each instance has its own in-memory H2 database. Both instances (8081 and 8084) seed the
 * same products in the same order, so the same id means the same product on either instance.
 * Disable with {@code product.seed.enabled=false}.
 */
@Configuration
@ConditionalOnProperty(name = "product.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    @Bean
    ApplicationRunner seedProducts(ProductRepository repository) {
        return args -> {
            if (repository.count() > 0) {
                return;
            }
            List<Product> products = List.of(
                    new Product("LAPTOP-15", "Laptop 15\"", "15-inch laptop, 16 GB RAM", new BigDecimal("999.00"), 10),
                    new Product("MOUSE-WL", "Wireless mouse", "Ergonomic wireless mouse", new BigDecimal("24.99"), 150),
                    new Product("KEYB-MECH", "Mechanical keyboard", "Mechanical keyboard, brown switches", new BigDecimal("89.50"), 40),
                    new Product("MONITOR-27", "27\" monitor", "27-inch 4K monitor", new BigDecimal("329.00"), 5),
                    new Product("HEADSET-BT", "Bluetooth headset", "Noise-cancelling headset", new BigDecimal("149.00"), 0));
            repository.saveAll(products);
            log.info("Seeded {} demo products", products.size());
        };
    }
}
