package com.example.payment.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI paymentServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Payment Service API")
                .version("1.0.0")
                .description("Simulated payments with operationId deduplication. "
                        + "Errors use RFC 9457 application/problem+json."));
    }
}
