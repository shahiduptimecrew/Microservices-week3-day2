package com.example.order.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI orderServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Order Service API")
                .version("1.0.0")
                .description("Order workflow with Idempotency-Key, HATEOAS (HAL) and a circuit breaker "
                        + "on payment. Errors use RFC 9457 application/problem+json."));
    }
}
