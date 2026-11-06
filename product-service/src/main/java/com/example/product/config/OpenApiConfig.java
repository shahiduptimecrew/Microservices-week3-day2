package com.example.product.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Two OpenAPI documents are available in Swagger UI (top-right drop-down):
 * <ul>
 *   <li><b>spec-first</b>: the hand-written contract {@code src/main/resources/openapi/product-api.yaml},
 *       served at {@code /openapi/product-api.yaml}. This is the source of truth agreed with clients.</li>
 *   <li><b>generated</b>: what springdoc reads from the code, at {@code /v3/api-docs}.
 *       Comparing the two shows whether the code still matches the contract.</li>
 * </ul>
 */
@Configuration
public class OpenApiConfig implements WebMvcConfigurer {

    @Bean
    OpenAPI productServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Product Service API (generated from code)")
                .version("1.0.0")
                .description("Product catalogue and stock. Errors use RFC 9457 application/problem+json."));
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/openapi/**").addResourceLocations("classpath:/openapi/");
    }
}
