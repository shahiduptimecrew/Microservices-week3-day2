package com.example.order;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test: the Spring context starts with the selected Boot / Cloud versions.
 * Eureka registration is switched off so the test does not need a running registry.
 */
@SpringBootTest(properties = "eureka.client.enabled=false")
class OrderServiceApplicationTests {

    @Test
    void contextLoads() {
    }
}
