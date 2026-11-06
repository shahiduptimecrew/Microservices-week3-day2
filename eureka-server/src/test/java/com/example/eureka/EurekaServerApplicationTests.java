package com.example.eureka;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Smoke test: the registry starts with the selected Boot / Cloud versions.
 * (The server needs its internal Eureka client beans, so the client is NOT disabled here;
 * application.yml already stops the registry from registering with itself.)
 */
@SpringBootTest
class EurekaServerApplicationTests {

    @Test
    void contextLoads() {
    }
}
